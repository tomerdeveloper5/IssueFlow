package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.DeletedTicketResponse;
import com.att.tdp.issueflow.api.dto.TicketCreateRequest;
import com.att.tdp.issueflow.api.dto.TicketImportResponse;
import com.att.tdp.issueflow.api.dto.TicketResponse;
import com.att.tdp.issueflow.api.dto.TicketUpdateRequest;
import com.att.tdp.issueflow.api.dto.WorkloadResponse;
import com.att.tdp.issueflow.domain.Ticket;
import com.att.tdp.issueflow.domain.TicketPriority;
import com.att.tdp.issueflow.domain.TicketStatus;
import com.att.tdp.issueflow.domain.User;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketDependencyRepository ticketDependencyRepository;
    private final ProjectService projectService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<TicketResponse> getByProject(Long projectId) {
        projectService.getActiveEntity(projectId);
        return ticketRepository.findByProjectIdAndDeletedFalse(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long ticketId) {
        return toResponse(getActiveEntity(ticketId));
    }

    @Transactional
    public TicketResponse create(TicketCreateRequest request) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setStatus(request.status());
        ticket.setPriority(request.priority());
        ticket.setType(request.type());
        ticket.setProject(projectService.getActiveEntity(request.projectId()));
        User assignee = resolveAssignee(request.projectId(), request.assigneeId());
        ticket.setAssignee(assignee);
        ticket.setDueDate(request.dueDate());
        ticket.setOverdue(false);
        ticket.setDeleted(false);
        Ticket saved = ticketRepository.save(ticket);
        auditLogService.logUserAction("CREATE", "TICKET", saved.getId());
        if (request.assigneeId() == null && assignee != null) {
            auditLogService.logSystemAction("AUTO_ASSIGN", "TICKET", saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional
    public void update(Long ticketId, TicketUpdateRequest request) {
        Ticket ticket = getActiveEntity(ticketId);
        if (ticket.getStatus() == TicketStatus.DONE) {
            throw new BadRequestException("A DONE ticket cannot be updated");
        }
        if (request.title() != null) {
            ticket.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            ticket.setDescription(request.description().trim());
        }
        if (request.status() != null && request.status() != ticket.getStatus()) {
            if (!ticket.getStatus().canTransitionTo(request.status())) {
                throw new BadRequestException(
                        "Invalid status transition: " + ticket.getStatus() + " -> " + request.status()
                );
            }
            if (request.status() == TicketStatus.DONE
                    && ticketDependencyRepository.existsByTicketIdAndBlockerStatusNot(ticketId, TicketStatus.DONE)) {
                throw new BadRequestException("Ticket cannot move to DONE while blockers are unresolved");
            }
            ticket.setStatus(request.status());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
            ticket.setOverdue(false);
        }
        if (request.assigneeId() != null) {
            ticket.setAssignee(userService.getEntity(request.assigneeId()));
        }
        if (request.dueDate() != null) {
            ticket.setDueDate(request.dueDate());
        }
        ticketRepository.save(ticket);
        auditLogService.logUserAction("UPDATE", "TICKET", ticketId);
    }

    @Transactional
    public void softDelete(Long ticketId) {
        Ticket ticket = getActiveEntity(ticketId);
        ticket.setDeleted(true);
        ticketRepository.save(ticket);
        auditLogService.logUserAction("DELETE", "TICKET", ticketId);
    }

    @Transactional(readOnly = true)
    public List<DeletedTicketResponse> getDeletedByProject(Long projectId) {
        projectService.getActiveEntity(projectId);
        return ticketRepository.findByProjectIdAndDeletedTrue(projectId).stream()
                .map(ticket -> new DeletedTicketResponse(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getStatus(),
                        ticket.getPriority(),
                        ticket.getType(),
                        ticket.getProject().getId()
                ))
                .toList();
    }

    @Transactional
    public void restore(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
        if (!ticket.isDeleted()) {
            throw new NotFoundException("Ticket not found: " + ticketId);
        }
        ticket.setDeleted(false);
        ticketRepository.save(ticket);
        auditLogService.logUserAction("RESTORE", "TICKET", ticketId);
    }

    @Transactional(readOnly = true)
    public List<WorkloadResponse> getProjectWorkload(Long projectId) {
        projectService.getActiveEntity(projectId);
        Map<Long, Long> openCounts = new HashMap<>();
        for (Object[] row : ticketRepository.countOpenTicketsByAssignee(projectId, TicketStatus.DONE)) {
            Long userId = (Long) row[0];
            Long count = (Long) row[1];
            openCounts.put(userId, count);
        }
        return userService.getDevelopersOrderedByRegistration().stream()
                .map(user -> new WorkloadResponse(user.getId(), user.getUsername(), openCounts.getOrDefault(user.getId(), 0L)))
                .sorted((a, b) -> Long.compare(a.openTicketCount(), b.openTicketCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long projectId) {
        projectService.getActiveEntity(projectId);
        List<Ticket> tickets = ticketRepository.findByProjectIdAndDeletedFalse(projectId);
        try {
            StringWriter writer = new StringWriter();
            CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                    .setHeader("id", "title", "description", "status", "priority", "type", "assigneeId")
                    .build());
            for (Ticket ticket : tickets) {
                printer.printRecord(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getDescription(),
                        ticket.getStatus(),
                        ticket.getPriority(),
                        ticket.getType(),
                        ticket.getAssignee() == null ? "" : ticket.getAssignee().getId()
                );
            }
            printer.flush();
            return writer.toString();
        } catch (IOException ex) {
            throw new BadRequestException("Failed to export tickets");
        }
    }

    @Transactional
    public TicketImportResponse importCsv(Long projectId, MultipartFile file) {
        projectService.getActiveEntity(projectId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is required");
        }
        int created = 0;
        List<String> errors = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(
                new StringReader(new String(file.getBytes())),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
        )) {
            for (CSVRecord record : parser) {
                try {
                    Ticket ticket = new Ticket();
                    ticket.setTitle(record.get("title").trim());
                    ticket.setDescription(record.get("description").trim());
                    ticket.setStatus(TicketStatus.valueOf(record.get("status").trim()));
                    ticket.setPriority(TicketPriority.valueOf(record.get("priority").trim()));
                    ticket.setType(com.att.tdp.issueflow.domain.TicketType.valueOf(record.get("type").trim()));
                    ticket.setProject(projectService.getActiveEntity(projectId));
                    String assigneeRaw = record.get("assigneeId").trim();
                    ticket.setAssignee(assigneeRaw.isEmpty() ? null : userService.getEntity(Long.valueOf(assigneeRaw)));
                    ticket.setOverdue(false);
                    ticket.setDeleted(false);
                    ticketRepository.save(ticket);
                    created++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Failed to import CSV");
        }
        auditLogService.logUserAction("IMPORT", "PROJECT", projectId);
        return new TicketImportResponse(created, errors.size(), errors);
    }

    @Transactional(readOnly = true)
    public Ticket getActiveEntity(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
        if (ticket.isDeleted() || ticket.getProject().isDeleted()) {
            throw new NotFoundException("Ticket not found: " + ticketId);
        }
        return ticket;
    }

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getType(),
                ticket.getProject().getId(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                ticket.getDueDate(),
                ticket.isOverdue()
        );
    }

    private User resolveAssignee(Long projectId, Long explicitAssigneeId) {
        if (explicitAssigneeId != null) {
            return userService.getEntity(explicitAssigneeId);
        }
        List<User> developers = userService.getDevelopersOrderedByRegistration();
        if (developers.isEmpty()) {
            return null;
        }
        User selected = null;
        long bestCount = Long.MAX_VALUE;
        for (User developer : developers) {
            long openCount = ticketRepository.countByProjectIdAndAssigneeIdAndDeletedFalseAndStatusNot(
                    projectId,
                    developer.getId(),
                    TicketStatus.DONE
            );
            if (openCount < bestCount) {
                bestCount = openCount;
                selected = developer;
            }
        }
        return selected;
    }

    @Transactional
    public void runEscalationCycleNow() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Ticket ticket : ticketRepository.findByDueDateBeforeAndDeletedFalseAndStatusNot(now, TicketStatus.DONE)) {
            TicketPriority previousPriority = ticket.getPriority();
            ticket.setPriority(previousPriority.escalate());
            if (previousPriority != ticket.getPriority()) {
                auditLogService.logSystemAction("AUTO_ESCALATE", "TICKET", ticket.getId());
            }
            if (ticket.getPriority() == TicketPriority.CRITICAL) {
                ticket.setOverdue(true);
            }
        }
    }
}



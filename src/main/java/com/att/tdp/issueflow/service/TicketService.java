package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.TicketCreateRequest;
import com.att.tdp.issueflow.api.dto.TicketResponse;
import com.att.tdp.issueflow.api.dto.TicketUpdateRequest;
import com.att.tdp.issueflow.domain.Ticket;
import com.att.tdp.issueflow.domain.TicketStatus;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.TicketRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final ProjectService projectService;
    private final UserService userService;

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
        ticket.setAssignee(request.assigneeId() == null ? null : userService.getEntity(request.assigneeId()));
        ticket.setDueDate(request.dueDate());
        ticket.setOverdue(false);
        ticket.setDeleted(false);
        return toResponse(ticketRepository.save(ticket));
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
            ticket.setStatus(request.status());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
            ticket.setOverdue(false);
        }
        if (request.assigneeId() != null) {
            ticket.setAssignee(userService.getEntity(request.assigneeId()));
        }
        ticket.setDueDate(request.dueDate());
        ticketRepository.save(ticket);
    }

    @Transactional
    public void softDelete(Long ticketId) {
        Ticket ticket = getActiveEntity(ticketId);
        ticket.setDeleted(true);
        ticketRepository.save(ticket);
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
}



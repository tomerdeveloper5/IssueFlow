package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.TicketDependencyResponse;
import com.att.tdp.issueflow.domain.Ticket;
import com.att.tdp.issueflow.domain.TicketDependency;
import com.att.tdp.issueflow.domain.TicketStatus;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketDependencyService {
    private final TicketDependencyRepository ticketDependencyRepository;
    private final TicketService ticketService;
    private final AuditLogService auditLogService;

    @Transactional
    public void addDependency(Long ticketId, Long blockerId) {
        Ticket ticket = ticketService.getActiveEntity(ticketId);
        Ticket blocker = ticketService.getActiveEntity(blockerId);
        if (ticketId.equals(blockerId)) {
            throw new BadRequestException("A ticket cannot depend on itself");
        }
        if (!ticket.getProject().getId().equals(blocker.getProject().getId())) {
            throw new BadRequestException("Dependency tickets must belong to the same project");
        }
        if (ticketDependencyRepository.existsByTicketIdAndBlockerId(ticketId, blockerId)) {
            throw new BadRequestException("Dependency already exists");
        }
        TicketDependency dependency = new TicketDependency();
        dependency.setTicket(ticket);
        dependency.setBlocker(blocker);
        ticketDependencyRepository.save(dependency);
        auditLogService.logUserAction("ADD_DEPENDENCY", "TICKET", ticketId);
    }

    @Transactional(readOnly = true)
    public List<TicketDependencyResponse> listDependencies(Long ticketId) {
        ticketService.getActiveEntity(ticketId);
        return ticketDependencyRepository.findByTicketId(ticketId).stream()
                .map(TicketDependency::getBlocker)
                .map(ticket -> new TicketDependencyResponse(ticket.getId(), ticket.getTitle(), ticket.getStatus()))
                .toList();
    }

    @Transactional
    public void removeDependency(Long ticketId, Long blockerId) {
        ticketService.getActiveEntity(ticketId);
        if (!ticketDependencyRepository.existsByTicketIdAndBlockerId(ticketId, blockerId)) {
            throw new NotFoundException("Dependency not found for ticket " + ticketId + " and blocker " + blockerId);
        }
        ticketDependencyRepository.deleteByTicketIdAndBlockerId(ticketId, blockerId);
        auditLogService.logUserAction("REMOVE_DEPENDENCY", "TICKET", ticketId);
    }

    @Transactional(readOnly = true)
    public void ensureCanMoveToDone(Long ticketId) {
        if (ticketDependencyRepository.existsByTicketIdAndBlockerStatusNot(ticketId, TicketStatus.DONE)) {
            throw new BadRequestException("Ticket cannot move to DONE while blockers are unresolved");
        }
    }
}


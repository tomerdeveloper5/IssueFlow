package com.att.tdp.issueflow.service;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketEscalationService {
    private final TicketService ticketService;

    @Scheduled(fixedDelayString = "${issueflow.escalation.fixed-delay-ms:60000}")
    @Transactional
    public void escalateOverdueTickets() {
        ticketService.runEscalationCycleNow();
    }
}


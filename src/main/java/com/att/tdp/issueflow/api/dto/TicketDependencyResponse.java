package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.TicketStatus;

public record TicketDependencyResponse(
        Long id,
        String title,
        TicketStatus status
) {
}


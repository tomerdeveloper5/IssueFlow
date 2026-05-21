package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.TicketPriority;
import com.att.tdp.issueflow.domain.TicketStatus;
import com.att.tdp.issueflow.domain.TicketType;

public record DeletedTicketResponse(
        Long id,
        String title,
        TicketStatus status,
        TicketPriority priority,
        TicketType type,
        Long projectId
) {
}


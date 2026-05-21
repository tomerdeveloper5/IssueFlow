package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.TicketPriority;
import com.att.tdp.issueflow.domain.TicketStatus;
import com.att.tdp.issueflow.domain.TicketType;
import java.time.OffsetDateTime;

public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketType type,
        Long projectId,
        Long assigneeId,
        OffsetDateTime dueDate,
        boolean isOverdue
) {
}




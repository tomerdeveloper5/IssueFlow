package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.TicketPriority;
import com.att.tdp.issueflow.domain.TicketStatus;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record TicketUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 3000) String description,
        TicketStatus status,
        TicketPriority priority,
        Long assigneeId,
        OffsetDateTime dueDate
) {
}




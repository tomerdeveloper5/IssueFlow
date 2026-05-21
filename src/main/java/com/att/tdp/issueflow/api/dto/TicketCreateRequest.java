package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.TicketPriority;
import com.att.tdp.issueflow.domain.TicketStatus;
import com.att.tdp.issueflow.domain.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record TicketCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 3000) String description,
        @NotNull TicketStatus status,
        @NotNull TicketPriority priority,
        @NotNull TicketType type,
        @NotNull Long projectId,
        Long assigneeId,
        OffsetDateTime dueDate
) {
}




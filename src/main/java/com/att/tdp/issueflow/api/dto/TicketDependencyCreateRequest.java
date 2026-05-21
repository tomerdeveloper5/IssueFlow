package com.att.tdp.issueflow.api.dto;

import jakarta.validation.constraints.NotNull;

public record TicketDependencyCreateRequest(@NotNull Long blockedBy) {
}


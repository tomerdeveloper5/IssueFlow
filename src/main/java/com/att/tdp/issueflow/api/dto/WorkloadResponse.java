package com.att.tdp.issueflow.api.dto;

public record WorkloadResponse(
        Long userId,
        String username,
        long openTicketCount
) {
}


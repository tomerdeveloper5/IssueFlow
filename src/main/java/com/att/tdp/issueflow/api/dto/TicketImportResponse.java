package com.att.tdp.issueflow.api.dto;

import java.util.List;

public record TicketImportResponse(
        int created,
        int failed,
        List<String> errors
) {
}


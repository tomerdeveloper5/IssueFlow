package com.att.tdp.issueflow.api.dto;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String errorCode,
        String explanation,
        String action,
        Map<String, String> validationErrors
) {
}




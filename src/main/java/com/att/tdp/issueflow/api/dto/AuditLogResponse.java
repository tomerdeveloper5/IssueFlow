package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.AuditActorType;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        Long performedBy,
        AuditActorType actor,
        Instant timestamp
) {
}


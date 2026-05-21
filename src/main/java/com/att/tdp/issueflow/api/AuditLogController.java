package com.att.tdp.issueflow.api;

import com.att.tdp.issueflow.api.dto.AuditLogResponse;
import com.att.tdp.issueflow.service.AuditLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogService auditLogService;

    @GetMapping
    public List<AuditLogResponse> getLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor
    ) {
        return auditLogService.search(entityType, entityId, action, actor);
    }
}


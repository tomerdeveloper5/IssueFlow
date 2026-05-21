package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.AuditLogResponse;
import com.att.tdp.issueflow.domain.AuditActorType;
import com.att.tdp.issueflow.domain.AuditLog;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void logUserAction(String action, String entityType, Long entityId) {
        Long performedBy = resolveCurrentUserId();
        create(action, entityType, entityId, performedBy, AuditActorType.USER);
    }

    @Transactional
    public void logSystemAction(String action, String entityType, Long entityId) {
        create(action, entityType, entityId, null, AuditActorType.SYSTEM);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> search(String entityType, Long entityId, String action, String actor) {
        AuditActorType actorType = actor == null ? null : AuditActorType.valueOf(actor.toUpperCase());
        return auditLogRepository.search(entityType, entityId, action, actorType).stream()
                .map(this::toResponse)
                .toList();
    }

    private void create(String action, String entityType, Long entityId, Long performedBy, AuditActorType actor) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPerformedBy(performedBy);
        log.setActor(actor);
        auditLogRepository.save(log);
    }

    private Long resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getPerformedBy(),
                log.getActor(),
                log.getTimestamp()
        );
    }
}


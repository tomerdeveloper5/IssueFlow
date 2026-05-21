package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.AuditActorType;
import com.att.tdp.issueflow.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("""
            select a
            from AuditLog a
            where (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:action is null or a.action = :action)
              and (:actor is null or a.actor = :actor)
            order by a.timestamp desc
            """)
    List<AuditLog> search(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("action") String action,
            @Param("actor") AuditActorType actor
    );
}


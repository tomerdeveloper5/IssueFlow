package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.Ticket;
import com.att.tdp.issueflow.domain.TicketStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByProjectIdAndDeletedFalse(Long projectId);

    List<Ticket> findByProjectIdAndDeletedTrue(Long projectId);

    List<Ticket> findByDueDateBeforeAndDeletedFalseAndStatusNot(OffsetDateTime now, TicketStatus status);

    long countByProjectIdAndAssigneeIdAndDeletedFalseAndStatusNot(Long projectId, Long assigneeId, TicketStatus status);

    @Query("""
            select t.assignee.id as userId, count(t.id) as openCount
            from Ticket t
            where t.project.id = :projectId
              and t.deleted = false
              and t.status <> :doneStatus
              and t.assignee is not null
            group by t.assignee.id
            """)
    List<Object[]> countOpenTicketsByAssignee(@Param("projectId") Long projectId, @Param("doneStatus") TicketStatus doneStatus);
}



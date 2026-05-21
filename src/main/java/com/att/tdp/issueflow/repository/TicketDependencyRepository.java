package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.TicketDependency;
import com.att.tdp.issueflow.domain.TicketStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {
    List<TicketDependency> findByTicketId(Long ticketId);

    boolean existsByTicketIdAndBlockerId(Long ticketId, Long blockerId);

    boolean existsByTicketIdAndBlockerStatusNot(Long ticketId, TicketStatus status);

    void deleteByTicketIdAndBlockerId(Long ticketId, Long blockerId);
}


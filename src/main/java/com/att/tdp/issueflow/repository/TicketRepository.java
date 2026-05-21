package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.Ticket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByProjectIdAndDeletedFalse(Long projectId);
}



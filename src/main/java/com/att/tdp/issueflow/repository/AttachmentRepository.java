package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.Attachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    Optional<Attachment> findByIdAndTicketId(Long id, Long ticketId);
}


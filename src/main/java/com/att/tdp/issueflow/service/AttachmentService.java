package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.AttachmentResponse;
import com.att.tdp.issueflow.domain.Attachment;
import com.att.tdp.issueflow.domain.Ticket;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AttachmentService {
    private static final long MAX_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "application/pdf",
            "text/plain"
    );

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;
    private final AuditLogService auditLogService;

    @Transactional
    public AttachmentResponse upload(Long ticketId, MultipartFile file) {
        Ticket ticket = ticketService.getActiveEntity(ticketId);
        validate(file);
        Attachment attachment = new Attachment();
        attachment.setTicket(ticket);
        attachment.setFilename(file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename());
        attachment.setContentType(file.getContentType());
        attachment.setSize(file.getSize());
        try {
            attachment.setContentBase64(Base64.getEncoder().encodeToString(file.getBytes()));
        } catch (IOException ex) {
            throw new BadRequestException("Unable to read attachment content");
        }
        Attachment saved = attachmentRepository.save(attachment);
        auditLogService.logUserAction("ADD_ATTACHMENT", "TICKET", ticketId);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long ticketId, Long attachmentId) {
        ticketService.getActiveEntity(ticketId);
        Attachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        attachmentRepository.delete(attachment);
        auditLogService.logUserAction("DELETE_ATTACHMENT", "TICKET", ticketId);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attachment file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Attachment exceeds the 10MB limit");
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Unsupported attachment content type");
        }
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicket().getId(),
                attachment.getFilename(),
                attachment.getContentType()
        );
    }
}


package com.att.tdp.issueflow.api.dto;

public record AttachmentResponse(
        Long id,
        Long ticketId,
        String filename,
        String contentType
) {
}


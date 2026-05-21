package com.att.tdp.issueflow.api.dto;

public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String content
) {
}




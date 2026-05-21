package com.att.tdp.issueflow.api.dto;

public record MentionedUserResponse(
        Long id,
        String username,
        String fullName
) {
}


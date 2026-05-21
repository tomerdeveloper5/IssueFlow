package com.att.tdp.issueflow.api.dto;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}




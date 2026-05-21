package com.att.tdp.issueflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record SecurityProperties(
        String secret,
        long expirationSeconds
) {
}



package com.att.tdp.issueflow.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TokenDenyListService {
    private final Map<String, Instant> deniedTokens = new ConcurrentHashMap<>();

    public void deny(String token, Instant expiresAt) {
        deniedTokens.put(token, expiresAt);
    }

    public boolean isDenied(String token) {
        cleanup();
        return deniedTokens.containsKey(token);
    }

    private void cleanup() {
        Instant now = Instant.now();
        deniedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}



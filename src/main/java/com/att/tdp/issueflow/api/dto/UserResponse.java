package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.UserRole;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        UserRole role
) {
}




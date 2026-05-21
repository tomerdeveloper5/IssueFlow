package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank
        @Size(min = 3, max = 60, message = "Username must be 3-60 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "Username can contain letters, numbers, dots, underscores, and hyphens only"
        )
        String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
        @Pattern(
                regexp = "^[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
                message = "Full name can contain letters, spaces, apostrophes, hyphens, and periods only"
        )
        String fullName,
        @NotNull UserRole role,
        @NotBlank
        @Size(min = 12, max = 72, message = "Password must be 12-72 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).+$",
                message = "Password must include upper, lower, number, and special character"
        )
        String password
) {
}




package com.att.tdp.issueflow.api.dto;

import com.att.tdp.issueflow.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
        @Pattern(
                regexp = "^[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
                message = "Full name can contain letters, spaces, apostrophes, hyphens, and periods only"
        )
        String fullName,
        @NotNull UserRole role
) {
}




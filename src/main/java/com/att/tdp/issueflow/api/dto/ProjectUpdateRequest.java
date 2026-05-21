package com.att.tdp.issueflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 1500) String description
) {
}




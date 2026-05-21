package com.att.tdp.issueflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 1500) String description,
        @NotNull Long ownerId
) {
}




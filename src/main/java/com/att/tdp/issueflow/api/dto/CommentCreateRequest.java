package com.att.tdp.issueflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotNull Long authorId,
        @NotBlank @Size(max = 3000) String content
) {
}




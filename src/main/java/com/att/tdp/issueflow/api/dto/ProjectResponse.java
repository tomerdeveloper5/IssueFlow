package com.att.tdp.issueflow.api.dto;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId
) {
}




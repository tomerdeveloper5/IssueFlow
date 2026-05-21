package com.att.tdp.issueflow.api.dto;

public record DeletedProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId
) {
}


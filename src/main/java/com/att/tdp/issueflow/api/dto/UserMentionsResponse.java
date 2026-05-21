package com.att.tdp.issueflow.api.dto;

import java.util.List;

public record UserMentionsResponse(
        List<CommentResponse> data,
        long total,
        int page
) {
}


package com.att.tdp.issueflow.api;

import com.att.tdp.issueflow.api.dto.CommentCreateRequest;
import com.att.tdp.issueflow.api.dto.CommentResponse;
import com.att.tdp.issueflow.api.dto.CommentUpdateRequest;
import com.att.tdp.issueflow.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @GetMapping
    public List<CommentResponse> getComments(@PathVariable Long ticketId) {
        return commentService.getByTicket(ticketId);
    }

    @PostMapping
    public CommentResponse createComment(@PathVariable Long ticketId, @Valid @RequestBody CommentCreateRequest request) {
        return commentService.create(ticketId, request);
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<Void> updateComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        commentService.update(ticketId, commentId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long ticketId, @PathVariable Long commentId) {
        commentService.delete(ticketId, commentId);
        return ResponseEntity.ok().build();
    }
}



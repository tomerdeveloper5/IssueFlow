package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.CommentCreateRequest;
import com.att.tdp.issueflow.api.dto.CommentResponse;
import com.att.tdp.issueflow.api.dto.CommentUpdateRequest;
import com.att.tdp.issueflow.domain.Comment;
import com.att.tdp.issueflow.domain.Ticket;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<CommentResponse> getByTicket(Long ticketId) {
        ticketService.getActiveEntity(ticketId);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse create(Long ticketId, CommentCreateRequest request) {
        Ticket ticket = ticketService.getActiveEntity(ticketId);
        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(userService.getEntity(request.authorId()));
        comment.setContent(request.content().trim());
        return toResponse(commentRepository.save(comment));
    }

    @Transactional
    public void update(Long ticketId, Long commentId, CommentUpdateRequest request) {
        Comment comment = getEntity(ticketId, commentId);
        comment.setContent(request.content().trim());
        commentRepository.save(comment);
    }

    @Transactional
    public void delete(Long ticketId, Long commentId) {
        Comment comment = getEntity(ticketId, commentId);
        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public Comment getEntity(Long ticketId, Long commentId) {
        ticketService.getActiveEntity(ticketId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
        if (!comment.getTicket().getId().equals(ticketId)) {
            throw new NotFoundException("Comment not found: " + commentId);
        }
        return comment;
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getContent()
        );
    }
}



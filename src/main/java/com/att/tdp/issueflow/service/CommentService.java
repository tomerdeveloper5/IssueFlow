package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.CommentCreateRequest;
import com.att.tdp.issueflow.api.dto.CommentResponse;
import com.att.tdp.issueflow.api.dto.CommentUpdateRequest;
import com.att.tdp.issueflow.api.dto.MentionedUserResponse;
import com.att.tdp.issueflow.api.dto.UserMentionsResponse;
import com.att.tdp.issueflow.domain.Comment;
import com.att.tdp.issueflow.domain.CommentMention;
import com.att.tdp.issueflow.domain.Ticket;
import com.att.tdp.issueflow.domain.User;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9._-]+)");

    private final CommentRepository commentRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final TicketService ticketService;
    private final UserService userService;
    private final AuditLogService auditLogService;

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
        Comment saved = commentRepository.save(comment);
        syncMentions(saved, request.content().trim());
        auditLogService.logUserAction("CREATE", "COMMENT", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void update(Long ticketId, Long commentId, CommentUpdateRequest request) {
        Comment comment = getEntity(ticketId, commentId);
        comment.setContent(request.content().trim());
        commentRepository.save(comment);
        syncMentions(comment, request.content().trim());
        auditLogService.logUserAction("UPDATE", "COMMENT", commentId);
    }

    @Transactional
    public void delete(Long ticketId, Long commentId) {
        Comment comment = getEntity(ticketId, commentId);
        commentMentionRepository.deleteByCommentId(commentId);
        commentRepository.delete(comment);
        auditLogService.logUserAction("DELETE", "COMMENT", commentId);
    }

    @Transactional(readOnly = true)
    public UserMentionsResponse getMentionsForUser(Long userId, int page, int pageSize) {
        userService.getEntity(userId);
        int sanitizedPageSize = Math.max(1, Math.min(pageSize, 200));
        int pageIndex = Math.max(0, page - 1);
        Page<Comment> comments = commentRepository.findMentionedForUser(userId, PageRequest.of(pageIndex, sanitizedPageSize));
        return new UserMentionsResponse(
                comments.getContent().stream().map(this::toResponse).toList(),
                comments.getTotalElements(),
                page
        );
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
                comment.getContent(),
                commentMentionRepository.findByCommentId(comment.getId()).stream()
                        .map(CommentMention::getUser)
                        .map(user -> new MentionedUserResponse(user.getId(), user.getUsername(), user.getFullName()))
                        .toList()
        );
    }

    private void syncMentions(Comment comment, String content) {
        Set<User> mentionedUsers = resolveMentionedUsers(content);
        commentMentionRepository.deleteByCommentId(comment.getId());
        for (User user : mentionedUsers) {
            CommentMention mention = new CommentMention();
            mention.setComment(comment);
            mention.setUser(user);
            commentMentionRepository.save(mention);
        }
    }

    private Set<User> resolveMentionedUsers(String content) {
        Matcher matcher = MENTION_PATTERN.matcher(content);
        Set<String> usernames = new LinkedHashSet<>();
        while (matcher.find()) {
            usernames.add(matcher.group(1).toLowerCase());
        }
        Set<User> users = new LinkedHashSet<>();
        for (String username : usernames) {
            users.add(userService.getByUsername(username));
        }
        return users;
    }
}



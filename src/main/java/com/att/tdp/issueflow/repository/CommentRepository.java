package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.Comment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    @Query("""
            select distinct c
            from Comment c
            join CommentMention m on m.comment.id = c.id
            where m.user.id = :userId
            order by c.createdAt desc
            """)
    Page<Comment> findMentionedForUser(@Param("userId") Long userId, Pageable pageable);
}



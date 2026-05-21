package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.CommentMention;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {
    List<CommentMention> findByCommentId(Long commentId);

    void deleteByCommentId(Long commentId);
}


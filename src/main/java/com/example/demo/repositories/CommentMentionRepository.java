package com.example.demo.repositories;

import com.example.demo.entity.CommentMention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {
    List<CommentMention> findByCommentId(Long commentId);
    void deleteByCommentId(Long commentId);
}
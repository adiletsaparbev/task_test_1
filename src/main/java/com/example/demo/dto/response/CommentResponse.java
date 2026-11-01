package com.example.demo.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentResponse {

    private Long id;
    private Long taskId;

    private Long authorId;
    private String authorName;

    private String content;

    private List<Long> mentionUserIds;
    private List<String> mentionUserNames;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
package com.example.demo.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskResponse {

    private Long id;
    private String title;
    private String description;

    private String status;
    private String priority;

    private LocalDateTime startDate;
    private LocalDateTime dueDate;

    private boolean isOverdue;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    private Long projectId;
    private String projectName;

    private Long creatorId;
    private String creatorName;

    // PM проекта
    private Long pmId;
    private String pmName;

    // назначенные исполнители
    private List<Long> assigneeIds;
    private List<String> assigneeNames;

    // пока можно оставить на будущее
    private List<String> tagNames;

    private int subtaskCount;
    private int completedSubtaskCount;
}
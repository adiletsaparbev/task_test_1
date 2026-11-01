package com.example.demo.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskHistoryResponse {

    private Long id;

    // Кто изменил
    private Long changedById;
    private String changedByName;

    // Что изменилось
    private String fieldName;   // "status", "priority", "dueDate", "assignees", "title"
    private String oldValue;
    private String newValue;

    private LocalDateTime changedAt;
}
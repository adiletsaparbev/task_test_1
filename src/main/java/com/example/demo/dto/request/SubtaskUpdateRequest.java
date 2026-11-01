package com.example.demo.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SubtaskUpdateRequest {
    private String title;

    // Было: Long assigneeId
    // Стало: List<Long> assigneeIds
    private List<Long> assigneeIds;

    private LocalDate dueDate;
}
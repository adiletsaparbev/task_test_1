package com.example.demo.dto.request;

import com.example.demo.enums.Priority;
import com.example.demo.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskUpdateRequest {

    private String title;
    private String description;
    private Priority priority;
    private TaskStatus status;
    private LocalDateTime startDate;
    private LocalDateTime dueDate;
    private List<Long> assigneeIds;
}
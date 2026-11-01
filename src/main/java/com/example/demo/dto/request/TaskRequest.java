package com.example.demo.dto.request;

import com.example.demo.enums.Priority;
import com.example.demo.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskRequest {

    @NotBlank(message = "Название задачи обязательно")
    private String title;

    private String description;

    @NotNull(message = "Проект обязателен")
    private Long projectId;

    private Priority priority = Priority.MEDIUM;

    // при создании можно не передавать, сервис сам поставит NEW
    private TaskStatus status = TaskStatus.NEW;

    private LocalDateTime startDate;

    @NotNull(message = "Срок выполнения обязателен")
    private LocalDateTime dueDate;

    @NotNull(message = "Исполнитель должен быть выбран")
    private List<Long> assigneeIds;

    private List<Long> tagIds;
    private List<Long> attachmentIds;

    // Список подзадач для создания вместе с задачей
    private List<SubtaskRequest> subtasks;
}
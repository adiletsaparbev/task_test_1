package com.example.demo.dto.request;

import com.example.demo.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SubtaskRequest {

    @NotBlank(message = "Название подзадачи обязательно")
    private String title;

    // Было: Long assigneeId — один исполнитель
    // Стало: List<Long> assigneeIds — несколько исполнителей
    @NotEmpty(message = "Необходимо указать хотя бы одного исполнителя")
    private List<Long> assigneeIds;

    private TaskStatus status = TaskStatus.NEW;

    @NotNull(message = "Срок выполнения обязателен")
    private LocalDate dueDate;
}
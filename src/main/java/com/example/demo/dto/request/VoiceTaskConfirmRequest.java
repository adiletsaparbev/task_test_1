package com.example.demo.dto.request;

import com.example.demo.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class VoiceTaskConfirmRequest {

    @NotNull(message = "assigneeId обязателен")
    private Long assigneeId;

    @NotBlank(message = "title обязателен")
    private String title;

    private String description;

    @NotNull(message = "dueDate обязателен")
    private LocalDateTime dueDate;

    private Priority priority = Priority.MEDIUM;

    private String transcript;

    private Double confidence;
}

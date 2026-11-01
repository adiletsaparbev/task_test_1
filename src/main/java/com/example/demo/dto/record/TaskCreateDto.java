package com.example.demo.dto.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TaskCreateDto(
        String title,
        String description,
        String status,       // можно null -> по умолчанию TODO
        String priority,     // можно null -> MEDIUM
        LocalDateTime dueDate,
        Integer estimate,
        Long assigneeId,
        List<String> tags
) {}

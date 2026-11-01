package com.example.demo.dto.record;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TaskUpdateDto(
        String title,
        String description,
        String priority,
        LocalDateTime dueDate,
        Integer estimate,
        Long assigneeId,
        List<String> tags
) {}
package com.example.demo.dto.record;

import com.example.demo.enums.ProjectStatus;

public record ProjectUpdateDto(
        String name,
        String description,
        ProjectStatus status,
        Long pmId,
        Long departmentId
) {}
package com.example.demo.dto.record;

import com.example.demo.enums.ProjectStatus;

public record ProjectDto(
        Long id,
        String name,
        String description,
        ProjectStatus status,
        UserShortDto pm,
        DepartmentShortDto department
) {}
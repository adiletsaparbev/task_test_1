package com.example.demo.dto.record;

public record UserDto(
        Long id,
        String fullName,
        String email,
        String role,
        boolean active,
        DepartmentShortDto department
) {}


package com.example.demo.dto.record;

public record MeDto(
        Long id,
        String fullName,
        String email,
        String role,
        Long departmentId,
        String departmentName
) {}
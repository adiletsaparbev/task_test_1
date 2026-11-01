package com.example.demo.dto.record;

public record UserShortDto(
        Long id,
        String fullName,
        String email,
        String role,
        boolean active
) {}
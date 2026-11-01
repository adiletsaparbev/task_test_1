package com.example.demo.dto.record;

public record UserCreateDto(
        String fullName,
        String email,
        String password,
        String role,
        Long departmentId,
        Boolean active // можно optional
) {}
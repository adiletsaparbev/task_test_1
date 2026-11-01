package com.example.demo.dto.record;

public record UserUpdateDto(
        String fullName,
        String role,
        Long departmentId,
        Boolean clearDepartment,
        Boolean active) {
}
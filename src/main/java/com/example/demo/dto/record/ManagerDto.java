package com.example.demo.dto.record;

import com.example.demo.entity.Department;
import com.example.demo.entity.User;

public record ManagerDto(
        Long id,
        String fullName,
        String email,
        String role
) {}
package com.example.demo.controllers;

import com.example.demo.dto.record.DepartmentDto;
import com.example.demo.services.AdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Публичный (для всех аутентифицированных пользователей) доступ к отделам.
 * Управление (CRUD) — только через AdminController (/api/admin/departments).
 */
@RestController
@RequestMapping("/api/departments")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DepartmentController {

    private final AdminService adminService;

    /** Получить список всех отделов — доступно всем авторизованным пользователям */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<DepartmentDto> getAll() {
        return adminService.getAllDepartments();
    }
}

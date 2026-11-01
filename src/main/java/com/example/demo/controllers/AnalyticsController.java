package com.example.demo.controllers;

import com.example.demo.dto.response.AnalyticsResponse;
import com.example.demo.dto.response.EmployeePerformanceResponse;
import com.example.demo.dto.response.TaskHistoryResponse;
import com.example.demo.services.AnalyticsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // =========================================================
    // GET /api/analytics
    // Полная аналитика: статусы + приоритеты + сотрудники
    //
    // Параметры (все опциональные):
    //   ?projectId=1        — фильтр по проекту
    //   ?departmentId=2     — фильтр по отделу (только ADMIN)
    //
    // Доступ: ADMIN, MANAGER
    // =========================================================
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                analyticsService.getAnalytics(
                        userDetails.getUsername(), projectId, departmentId));
    }

    // =========================================================
    // GET /api/analytics/tasks/by-status
    // Количество задач по статусам
    // Пример ответа: { "NEW": 5, "IN_PROGRESS": 3, "ON_REVIEW": 2, "DONE": 10 }
    // =========================================================
    @GetMapping("/tasks/by-status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Long>> tasksByStatus(
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                analyticsService.getTasksByStatus(userDetails.getUsername(), projectId));
    }

    // =========================================================
    // GET /api/analytics/tasks/by-priority
    // Количество задач по приоритетам
    // Пример ответа: { "LOW": 4, "MEDIUM": 8, "HIGH": 2 }
    // =========================================================
    @GetMapping("/tasks/by-priority")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Long>> tasksByPriority(
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                analyticsService.getTasksByPriority(userDetails.getUsername(), projectId));
    }

    // =========================================================
    // GET /api/analytics/employees/performance
    // Производительность сотрудников
    // ?departmentId=1 — опционально
    // =========================================================
    @GetMapping("/employees/performance")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<EmployeePerformanceResponse>> employeePerformance(
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                analyticsService.getEmployeePerformance(
                        userDetails.getUsername(), departmentId));
    }

    // =========================================================
    // GET /api/analytics/tasks/{taskId}/history
    // История изменений конкретной задачи (кто что менял)
    // Доступ: все роли (у кого есть доступ к задаче)
    // =========================================================
    @GetMapping("/tasks/{taskId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<TaskHistoryResponse>> taskHistory(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                analyticsService.getTaskHistory(userDetails.getUsername(), taskId));
    }
}
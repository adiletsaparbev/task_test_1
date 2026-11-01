package com.example.demo.controllers;

import com.example.demo.dto.request.TaskRequest;
import com.example.demo.dto.request.TaskUpdateRequest;
import com.example.demo.dto.response.TaskResponse;
import com.example.demo.services.TaskService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tasks")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // Список задач (фильтрация по роли внутри сервиса)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<TaskResponse>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskService.getAllTasks(userDetails.getUsername()));
    }

    // Поиск по названию и/или тегам
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<TaskResponse>> search(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) List<Long> tagIds,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                taskService.searchTasks(title, tagIds, userDetails.getUsername()));
    }

    // Создание задачи
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                taskService.createTask(request, userDetails.getUsername()));
    }

    // Получить одну задачу
    @GetMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<TaskResponse> getOne(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                taskService.getTaskById(taskId, userDetails.getUsername()));
    }

    // Обновить задачу
    @PatchMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<TaskResponse> update(
            @PathVariable Long taskId,
            @RequestBody TaskUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            log.info("Update task {} with status={} by user {}", taskId, request.getStatus(),
                    userDetails.getUsername());
            return ResponseEntity.ok(
                    taskService.updateTask(taskId, request, userDetails.getUsername()));
        } catch (Exception e) {
            log.error("Error updating task {} : {}", taskId, e.getMessage(), e);
            throw e;
        }
    }

    // Удалить задачу
    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskService.deleteTask(taskId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
package com.example.demo.controllers;

import com.example.demo.dto.request.SubtaskRequest;
import com.example.demo.dto.request.SubtaskStatusRequest;
import com.example.demo.dto.request.SubtaskUpdateRequest;
import com.example.demo.dto.response.SubtaskResponse;
import com.example.demo.services.SubtaskService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SubtaskController {

    private final SubtaskService subtaskService;

    @GetMapping("/api/tasks/{taskId}/subtasks")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<SubtaskResponse>> getAll(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subtaskService.getByTask(taskId, userDetails.getUsername()));
    }

    @PostMapping("/api/tasks/{taskId}/subtasks")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<SubtaskResponse> create(
            @PathVariable Long taskId,
            @Valid @RequestBody SubtaskRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subtaskService.create(taskId, request, userDetails.getUsername()));
    }

    @PatchMapping("/api/subtasks/{subtaskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<SubtaskResponse> update(
            @PathVariable Long subtaskId,
            @RequestBody SubtaskUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subtaskService.update(subtaskId, request, userDetails.getUsername()));
    }

    @PatchMapping("/api/subtasks/{subtaskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TEAM')")
    public ResponseEntity<SubtaskResponse> updateStatus(
            @PathVariable Long subtaskId,
            @Valid @RequestBody SubtaskStatusRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subtaskService.updateStatus(subtaskId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/api/subtasks/{subtaskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(
            @PathVariable Long subtaskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        subtaskService.delete(subtaskId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
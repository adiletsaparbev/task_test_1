package com.example.demo.controllers;

import com.example.demo.dto.request.ProjectRequest;
import com.example.demo.dto.response.ProjectResponse;
import com.example.demo.services.ProjectService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<ProjectResponse>> getAll(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(projectService.getAllAsResponse(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<ProjectResponse> getById(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(projectService.getByIdAsResponse(id, userDetails.getUsername()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<ProjectResponse> create(
            @RequestBody ProjectRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(projectService.createAsResponse(request, userDetails.getUsername()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ProjectResponse> update(@PathVariable Long id, @RequestBody ProjectRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(projectService.updateAsResponse(id, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

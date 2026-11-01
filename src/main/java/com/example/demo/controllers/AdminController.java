package com.example.demo.controllers;

import com.example.demo.dto.record.*;
import com.example.demo.entity.Department;
import com.example.demo.entity.Project;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.AdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Полный доступ только для админа
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    // --- Профиль (Доступно всем авторизованным) ---
    @GetMapping("/auth/me")
    public ResponseEntity<MeDto> me(Principal principal) {
        User u = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Department d = u.getDepartment();

        return ResponseEntity.ok(new MeDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole().name(),
                d != null ? d.getId() : null,
                d != null ? d.getName() : null));
    }

    // --- Отделы ---
    @GetMapping("/departments")
    public List<DepartmentDto> getDepartments() {
        return adminService.getAllDepartments();
    }

    @PostMapping("/departments")
    public DepartmentDto createDept(@RequestBody Department dept) {
        return adminService.createDepartment(dept);
    }

    @PatchMapping("/departments/{id}")
    public DepartmentDto updateDept(@PathVariable Long id, @RequestBody Department dept) {
        return adminService.updateDepartment(id, dept);
    }

    @DeleteMapping("/departments/{id}")
    public void deleteDept(@PathVariable Long id) {
        adminService.deleteDepartment(id);
    }

    // --- Пользователи ---
    @GetMapping("/users")
    public List<UserDto> getUsers(@RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Long departmentId) {
        return adminService.findUsers(query, role, departmentId);
    }

    @PostMapping("/users")
    public UserDto createUser(@RequestBody UserCreateDto dto) {
        return adminService.createUser(dto);
    }

    @PatchMapping("/users/{id}")
    public UserDto updateUser(@PathVariable Long id, @RequestBody UserUpdateDto dto) {
        return adminService.updateUser(id, dto);
    }

    @PatchMapping("/users/{id}/status")
    public UserDto updateStatus(@PathVariable Long id, @RequestParam boolean active) {
        return adminService.setUserStatus(id, active);
    }

    @DeleteMapping("users/{id}")
    public void deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
    }

    // --- Проекты ---
    @GetMapping("/projects")
    public List<ProjectDto> getProjects() {
        return adminService.getAllProjects();
    }

    @PostMapping("/projects")
    public ProjectDto createProject(@RequestBody ProjectCreateDto dto) {
        return adminService.createProject(dto);
    }

    @GetMapping("/projects/{id}")
    public ProjectDto getProject(@PathVariable Long id) {
        return adminService.getProjectById(id);
    }

    @PatchMapping("/projects/{id}")
    public ProjectDto updateProject(@PathVariable Long id, @RequestBody ProjectUpdateDto dto) {
        return adminService.updateProject(id, dto);
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        adminService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}

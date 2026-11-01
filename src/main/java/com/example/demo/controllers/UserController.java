package com.example.demo.controllers;

import com.example.demo.dto.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.repositories.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<UserResponse>> getAll() {
        List<UserResponse> users = userRepository.findAll().stream()
                .filter(User::isActive)
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private UserResponse toResponse(User u) {
        UserResponse r = new UserResponse();
        r.setId(u.getId());
        r.setFullName(u.getFullName());
        r.setEmail(u.getEmail());
        r.setRole(u.getRole().name());
        if (u.getDepartment() != null) {
            r.setDepartmentId(u.getDepartment().getId());
            r.setDepartmentName(u.getDepartment().getName());
        }
        return r;
    }
}

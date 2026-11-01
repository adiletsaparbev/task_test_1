package com.example.demo.controllers;

import com.example.demo.dto.response.EmployeeWorkloadDTO;
import com.example.demo.dto.response.EmployeeWorkloadDetailsDTO;
import com.example.demo.services.EmployeeWorkloadService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class EmployeeWorkloadController {

    private final EmployeeWorkloadService employeeWorkloadService;

    @GetMapping("/workload")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<List<EmployeeWorkloadDTO>> getEmployeesWorkload(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(employeeWorkloadService.getEmployeesWorkload(userDetails.getUsername()));
    }

    @GetMapping("/{id}/workload")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<EmployeeWorkloadDetailsDTO> getEmployeeWorkloadDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(employeeWorkloadService.getEmployeeWorkloadDetails(id, userDetails.getUsername()));
    }
}

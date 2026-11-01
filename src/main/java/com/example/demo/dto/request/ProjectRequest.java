package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectRequest {
    @NotBlank
    private String name;
    private String description;
    private Long pmId;
    private Long departmentId;
    private List<Long> memberIds;
    private LocalDate deadline;
}
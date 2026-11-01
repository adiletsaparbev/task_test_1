package com.example.demo.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private String status;

    private Long pmId;
    private String pmName;

    private Long departmentId;
    private String departmentName;

    private List<Long> memberIds;
    private List<String> memberNames;

    private int taskCount;
    private LocalDate deadline;
}
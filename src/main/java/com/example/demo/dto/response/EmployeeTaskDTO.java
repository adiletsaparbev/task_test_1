package com.example.demo.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeTaskDTO {
    private Long id;
    private String title;
    private String project;
    private String priority;
    private String status;
    private LocalDateTime deadline;
    private boolean isOverdue;
}

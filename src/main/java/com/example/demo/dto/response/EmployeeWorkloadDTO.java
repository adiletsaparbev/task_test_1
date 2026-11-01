package com.example.demo.dto.response;

import lombok.Data;

@Data
public class EmployeeWorkloadDTO {
    private Long id;
    private String name;
    private String position;
    private String department;

    private long totalTasks;
    private long activeTasks;
    private long completedTasks;
    private long overdueTasks;

    private double workloadPercent;
    private String workloadStatus;
}

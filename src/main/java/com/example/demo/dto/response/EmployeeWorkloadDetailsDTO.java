package com.example.demo.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EmployeeWorkloadDetailsDTO {

    private Employee employee;
    private Statistics statistics;
    private List<EmployeeProjectDTO> projects = new ArrayList<>();
    private TaskStatusStats taskStatusStats;
    private List<EmployeeTaskDTO> tasks = new ArrayList<>();

    @Data
    public static class Employee {
        private Long id;
        private String name;
        private String position;
        private String department;
        private String role;
    }

    @Data
    public static class Statistics {
        private long totalTasks;
        private long activeTasks;
        private long completedTasks;
        private long overdueTasks;
        private double workloadPercent;
        private String workloadStatus;
    }

    @Data
    public static class TaskStatusStats {
        private long active;
        private long completed;
        private long overdue;
    }
}

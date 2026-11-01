package com.example.demo.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AnalyticsResponse {

    // Количество задач по статусам: { "NEW": 5, "IN_PROGRESS": 3, "DONE": 10, ... }
    private Map<String, Long> tasksByStatus;

    // Количество задач по приоритетам: { "LOW": 4, "MEDIUM": 8, "HIGH": 2 }
    private Map<String, Long> tasksByPriority;

    // Производительность сотрудников
    private List<EmployeePerformanceResponse> employeePerformance;
}
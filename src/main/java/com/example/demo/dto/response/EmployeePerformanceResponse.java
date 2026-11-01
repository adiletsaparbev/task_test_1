package com.example.demo.dto.response;

import lombok.Data;

@Data
public class EmployeePerformanceResponse {

    private Long userId;
    private String fullName;
    private String role;
    private String departmentName;

    private int totalAssigned;    // Всего задач назначено
    private long completedTasks;  // Выполненных задач
    private long overdueTasks;    // Просроченных (dueDate прошёл, статус != DONE)
    private long inProgressTasks; // В работе прямо сейчас
}
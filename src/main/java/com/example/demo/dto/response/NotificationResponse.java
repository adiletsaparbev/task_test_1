package com.example.demo.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long id;

    // Тип уведомления:
    // TASK_ASSIGNED   — назначена новая задача
    // STATUS_CHANGED  — изменился статус задачи
    // NEW_COMMENT     — новый комментарий к задаче
    // MENTION         — вас упомянули в комментарии
    // TASK_OVERDUE    — задача просрочена (от планировщика, каждый день в 09:00)
    private String type;

    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;

    // Ссылка на задачу (для перехода по клику на уведомление)
    private Long taskId;
    private String taskTitle;
}
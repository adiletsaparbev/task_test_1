package com.example.demo.enums;

/**
 * Центральное хранилище констант типов уведомлений.
 * Используйте эти константы вместо строковых литералов везде, где создаётся или
 * обрабатывается уведомление (TaskService, CommentService,
 * TelegramNotificationService и т.д.)
 */
public final class NotificationTypes {

    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String NEW_COMMENT = "NEW_COMMENT";
    public static final String MENTION = "MENTION";
    public static final String TASK_OVERDUE = "TASK_OVERDUE";

    private NotificationTypes() {
        throw new UnsupportedOperationException("Utility class");
    }
}

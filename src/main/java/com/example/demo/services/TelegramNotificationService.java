package com.example.demo.services;

import com.example.demo.entity.Task;
import com.example.demo.entity.User;
import com.example.demo.enums.NotificationTypes;
import com.example.demo.enums.Priority;
import com.example.demo.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final TelegramBotClient telegramBotClient;

    @Value("${telegram.notifications.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Маппинг enum-имён статусов на русские метки для Telegram-уведомлений. */
    private static final Map<String, String> STATUS_RU = Map.of(
            "NEW",         "Новая",
            "IN_PROGRESS", "В работе",
            "REVIEW",      "На проверке",
            "DONE",        "Выполнена"
    );

    @Async
    public void send(User user, String type, String message, Task task) {
        if (user == null)
            return;
        if (!user.isTelegramNotificationsEnabled()) {
            log.debug("Telegram skip: notifications disabled for userId={}", user.getId());
            return;
        }
        if (user.getTelegramChatId() == null || user.getTelegramChatId().isBlank()) {
            log.debug("Telegram skip: no chatId for userId={}", user.getId());
            return;
        }
        if (!telegramBotClient.isConfigured()) {
            log.warn("Telegram bot not configured (token/enabled missing)");
            return;
        }
        log.info("Sending Telegram notification type={} to userId={} chatId=***{}",
                type, user.getId(),
                user.getTelegramChatId().length() >= 4
                        ? user.getTelegramChatId().substring(user.getTelegramChatId().length() - 4)
                        : user.getTelegramChatId());
        String telegramText = buildText(type, message, task, user);
        boolean ok = telegramBotClient.sendMessage(user.getTelegramChatId(), telegramText);
        if (!ok) {
            log.warn("Telegram notification FAILED for userId={} type={}", user.getId(), type);
        }
    }

    private String buildText(String type, String message, Task task, User recipient) {
        StringBuilder sb = new StringBuilder();
        NotificationMeta meta = metaFor(type);

        sb.append(meta.icon()).append(" *").append(meta.title()).append("*\n");
        sb.append(
                "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");

        String firstName = recipient != null && recipient.getFullName() != null
                ? recipient.getFullName().split(" ")[0]
                : null;
        if (firstName != null && !firstName.isBlank()) {
            sb.append("\u041f\u0440\u0438\u0432\u0435\u0442, ").append(esc(firstName)).append("! \ud83d\udc4b\n\n");
        }
        if (message != null && !message.isBlank()) {
            sb.append(esc(translateStatusesInMessage(message))).append("\n");
        }
        if (task != null && task.getId() != null) {
            sb.append("\n");
            sb.append("\ud83d\udccb *\u0417\u0430\u0434\u0430\u0447\u0430:* ").append(esc(task.getTitle()))
                    .append("\n");
            if (task.getProject() != null && task.getProject().getName() != null) {
                sb.append("\ud83d\udcc1 *\u041f\u0440\u043e\u0435\u043a\u0442:* ")
                        .append(esc(task.getProject().getName())).append("\n");
            }
            if (task.getStatus() != null) {
                sb.append("\ud83d\udd16 *\u0421\u0442\u0430\u0442\u0443\u0441:* ").append(statusLabel(task.getStatus()))
                        .append("\n");
            }
            if (task.getPriority() != null) {
                sb.append("\u26a1 *\u041f\u0440\u0438\u043e\u0440\u0438\u0442\u0435\u0442:* ")
                        .append(priorityLabel(task.getPriority())).append("\n");
            }
            if (task.getDueDate() != null) {
                boolean overdue = task.getDueDate().isBefore(LocalDateTime.now());
                String dueDateStr = task.getDueDate().format(DATE_FMT);
                sb.append(overdue ? "\ud83d\udd34" : "\ud83d\udcc5")
                        .append(" *\u0414\u0435\u0434\u043b\u0430\u0439\u043d:* ").append(dueDateStr);
                if (overdue)
                    sb.append(" _(\u043f\u0440\u043e\u0441\u0440\u043e\u0447\u0435\u043d\u043e)_");
                sb.append("\n");
            }
        }
        sb.append(
                "\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        return sb.toString();
    }

    // NotificationMeta: combines icon + title to avoid duplicating a switch
    private record NotificationMeta(String icon, String title) {
    }

    private NotificationMeta metaFor(String type) {
        if (type == null)
            return new NotificationMeta("\ud83d\udd14",
                    "\u0423\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u0435");
        return switch (type) {
            case NotificationTypes.TASK_ASSIGNED -> new NotificationMeta("\ud83d\udccc",
                    "\u0412\u0430\u043c \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0430 \u0437\u0430\u0434\u0430\u0447\u0430");
            case NotificationTypes.STATUS_CHANGED -> new NotificationMeta("\ud83d\udd04",
                    "\u0421\u0442\u0430\u0442\u0443\u0441 \u0437\u0430\u0434\u0430\u0447\u0438 \u0438\u0437\u043c\u0435\u043d\u0451\u043d");
            case NotificationTypes.NEW_COMMENT -> new NotificationMeta("\ud83d\udcac",
                    "\u041d\u043e\u0432\u044b\u0439 \u043a\u043e\u043c\u043c\u0435\u043d\u0442\u0430\u0440\u0438\u0439");
            case NotificationTypes.MENTION -> new NotificationMeta("\ud83d\udce3",
                    "\u0412\u0430\u0441 \u0443\u043f\u043e\u043c\u044f\u043d\u0443\u043b\u0438");
            case NotificationTypes.TASK_OVERDUE -> new NotificationMeta("\ud83d\udea8",
                    "\u0417\u0430\u0434\u0430\u0447\u0430 \u043f\u0440\u043e\u0441\u0440\u043e\u0447\u0435\u043d\u0430");
            default -> new NotificationMeta("\ud83d\udd14",
                    "\u0423\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u0435");
        };
    }

    /**
     * Раньше экранировал Markdown-символы, но parse_mode убран — теперь plain text.
     * Метод оставлен для совместимости, просто возвращает текст как есть.
     */
    private static String esc(String text) {
        if (text == null)
            return "—";
        return text;
    }

    private String statusLabel(TaskStatus status) {
        if (status == null) return "\u2014";
        return STATUS_RU.getOrDefault(status.name(), status.name());
    }

    /** Заменяет английские имена статусов в тексте сообщения на русские метки из STATUS\_RU. */
    private static String translateStatusesInMessage(String msg) {
        if (msg == null) return null;
        String result = msg;
        for (Map.Entry<String, String> e : STATUS_RU.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private String priorityLabel(Priority priority) {
        return switch (priority) {
            case LOW -> "\ud83d\udfe2 \u041d\u0438\u0437\u043a\u0438\u0439";
            case MEDIUM -> "\ud83d\udfe1 \u0421\u0440\u0435\u0434\u043d\u0438\u0439";
            case HIGH -> "\ud83d\udfe0 \u0412\u044b\u0441\u043e\u043a\u0438\u0439";
            case URGENT -> "\ud83d\udd34 \u0421\u0440\u043e\u0447\u043d\u043e";
        };
    }
}

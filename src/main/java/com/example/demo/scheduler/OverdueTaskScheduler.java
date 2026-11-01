package com.example.demo.scheduler;

import com.example.demo.entity.Task;
import com.example.demo.entity.TaskAssignee;
import com.example.demo.enums.TaskStatus;
import com.example.demo.repositories.TaskRepository;
import com.example.demo.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueTaskScheduler {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    // =========================================================
    // Запускается каждый день в 09:00
    // Находит все просроченные задачи (dueDate < сегодня, статус != DONE)
    // Помечает их isOverdue = true
    // Отправляет уведомление исполнителям и создателю
    // =========================================================
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void notifyOverdueTasks() {
        LocalDate today = LocalDate.now();
        log.info("[OverdueTaskScheduler] Запуск проверки просроченных задач: {}", LocalDateTime.now());

        // Ищем задачи где dueDate < сегодня, статус не DONE и ещё не помечены как просроченные
        List<Task> overdueTasks = taskRepository.findAll().stream()
                .filter(t -> t.getDueDate() != null)
                .filter(t -> t.getDueDate().isBefore(today.atStartOfDay()))
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .toList();

        log.info("[OverdueTaskScheduler] Найдено просроченных задач: {}", overdueTasks.size());

        for (Task task : overdueTasks) {
            // Помечаем задачу как просроченную если ещё не помечена
            if (!task.isOverdue()) {
                task.setOverdue(true);
                taskRepository.save(task);
            }

            String message = "⚠️ Задача просрочена: \"" + task.getTitle()
                    + "\". Срок был: " + task.getDueDate();

            // Уведомляем создателя задачи
            if (task.getCreator() != null) {
                // Проверяем — не отправляли ли уже сегодня уведомление об этой задаче
                boolean alreadyNotified = notificationService
                        .wasOverdueNotificationSentToday(task.getId(), task.getCreator().getId());

                if (!alreadyNotified) {
                    notificationService.send(task.getCreator(), "TASK_OVERDUE", message, task);
                }
            }

            // Уведомляем всех исполнителей
            if (task.getAssignees() != null) {
                for (TaskAssignee assignee : task.getAssignees()) {
                    if (assignee.getUser() == null) continue;

                    // Не дублируем если создатель = исполнитель
                    if (task.getCreator() != null
                            && assignee.getUser().getId().equals(task.getCreator().getId())) {
                        continue;
                    }

                    boolean alreadyNotified = notificationService
                            .wasOverdueNotificationSentToday(task.getId(), assignee.getUser().getId());

                    if (!alreadyNotified) {
                        notificationService.send(assignee.getUser(), "TASK_OVERDUE", message, task);
                    }
                }
            }
        }

        log.info("[OverdueTaskScheduler] Завершено.");
    }
}
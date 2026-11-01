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

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueNotificationScheduler {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    // =========================================================
    // Каждую ночь в 00:05 — проверяем просроченные задачи
    // и уведомляем исполнителей
    // =========================================================
    @Scheduled(cron = "0 5 0 * * *")
    public void notifyOverdueTasks() {
        LocalDate today = LocalDate.now();

        // Берём все задачи, у которых dueDate < сегодня и статус != DONE
        List<Task> overdueTasks = taskRepository.findOverdueTasks(today);

        log.info("Overdue scheduler: найдено {} просроченных задач", overdueTasks.size());

        for (Task task : overdueTasks) {
            // Помечаем задачу как просроченную
            if (!task.isOverdue()) {
                task.setOverdue(true);
                taskRepository.save(task);
            }

            String message = "Задача просрочена: \"" + task.getTitle() + "\""
                    + " (срок был: " + task.getDueDate() + ")";

            // Уведомляем каждого исполнителя
            if (task.getAssignees() != null) {
                task.getAssignees().stream()
                        .map(TaskAssignee::getUser)
                        .filter(u -> u != null)
                        .forEach(u -> notificationService.send(u, "OVERDUE", message, task));
            }
        }
    }
}
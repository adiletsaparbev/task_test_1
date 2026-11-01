package com.example.demo.services;

import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.entity.*;
import com.example.demo.enums.Role;
import com.example.demo.exception.AccessDeniedException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TelegramNotificationService telegramNotificationService;

    // =========================================================
    // Отправить уведомление одному пользователю
    // =========================================================
    public void send(User user, String type, String message, Task task) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .message(message)
                .task(task)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        // Принудительно загружаем lazy-прокси пока сессия принадлежит HTTP-потоку.
        // Без этого @Async-поток пытается обратиться к той же Hibernate-сессии
        // одновременно с HTTP-потоком → IllegalStateException (Illegal pop()).
        if (user != null)
            Hibernate.initialize(user);
        if (task != null) {
            Hibernate.initialize(task);
            if (task.getProject() != null)
                Hibernate.initialize(task.getProject());
        }

        // Внешний канал: Telegram (не должен ломать основной поток)
        telegramNotificationService.send(user, type, message, task);
    }

    // =========================================================
    // Уведомить всех участников задачи (создатель + исполнители)
    // exclude — автор действия (не получает своё уведомление)
    // =========================================================
    public void notifyTaskParticipants(Task task, String type, String message, User exclude) {
        if (task.getCreator() != null && !task.getCreator().getId().equals(exclude.getId())) {
            send(task.getCreator(), type, message, task);
        }
        if (task.getAssignees() != null) {
            task.getAssignees().stream()
                    .map(TaskAssignee::getUser)
                    .filter(u -> u != null && !u.getId().equals(exclude.getId()))
                    .forEach(u -> send(u, type, message, task));
        }
    }

    // =========================================================
    // Проверка — уже отправляли TASK_OVERDUE сегодня?
    // Используется планировщиком, чтобы не дублировать уведомления
    // =========================================================
    public boolean wasOverdueNotificationSentToday(Long taskId, Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        return notificationRepository
                .existsByTaskIdAndUserIdAndTypeAndCreatedAtBetween(
                        taskId, userId, "TASK_OVERDUE", startOfDay, endOfDay);
    }

    // =========================================================
    // Получить все уведомления пользователя
    // =========================================================
    public List<NotificationResponse> getAll(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    // =========================================================
    // Только непрочитанные
    // =========================================================
    public List<NotificationResponse> getUnread(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalse(userId)
                .stream().map(this::toResponse).toList();
    }

    // =========================================================
    // Количество непрочитанных (для бейджа колокольчика)
    // =========================================================
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // =========================================================
    // Пометить одно уведомление как прочитанное
    // =========================================================
    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    // =========================================================
    // Пометить все уведомления пользователя как прочитанные
    // =========================================================
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    // =========================================================
    // При открытии задачи — читаем все уведомления по ней
    // =========================================================
    public void markTaskNotificationsAsRead(Long taskId, Long userId) {
        List<Notification> notifications = notificationRepository.findByTaskIdAndUserIdAndIsReadFalse(taskId, userId);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    // =========================================================
    // Удалить одно уведомление — только ADMIN
    // =========================================================
    public void delete(Long notificationId, User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Удалять уведомления может только администратор");
        }
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Уведомление не найдено"));
        notificationRepository.delete(notification);
    }

    // =========================================================
    // Удалить все уведомления конкретного пользователя — только ADMIN
    // =========================================================
    public void deleteAllForUser(Long targetUserId, User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Удалять уведомления может только администратор");
        }
        List<Notification> all = notificationRepository.findByUserIdOrderByCreatedAtDesc(targetUserId);
        notificationRepository.deleteAll(all);
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.setId(n.getId());
        r.setType(n.getType());
        r.setMessage(n.getMessage());
        r.setRead(n.isRead());
        r.setCreatedAt(n.getCreatedAt());
        if (n.getTask() != null) {
            r.setTaskId(n.getTask().getId());
            r.setTaskTitle(n.getTask().getTitle());
        }
        return r;
    }
}
package com.example.demo.services;

import com.example.demo.dto.request.CommentRequest;
import com.example.demo.dto.request.CommentUpdateRequest;
import com.example.demo.dto.response.CommentResponse;
import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentMention;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskAssignee;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.exception.AccessDeniedException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repositories.CommentMentionRepository;
import com.example.demo.repositories.CommentRepository;
import com.example.demo.repositories.TaskRepository;
import com.example.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // =========================================================
    // 1. Получение комментариев по задаче
    // =========================================================
    public List<CommentResponse> getCommentsByTask(Long taskId, String sort, String email) {
        Task task = findTask(taskId);
        User user = getUser(email);

        validateTaskAccess(user, task, "VIEW");

        List<Comment> comments = commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);

        if ("desc".equalsIgnoreCase(sort)) {
            comments = comments.stream()
                    .sorted(Comparator.comparing(Comment::getCreatedAt).reversed())
                    .toList();
        }

        return comments.stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // 2. Добавление комментария
    // После добавления уведомляем:
    // — создателя задачи
    // — всех исполнителей
    // — упомянутых пользователей (@mention)
    // Кроме автора комментария
    // =========================================================
    public CommentResponse addComment(Long taskId, CommentRequest request, String authorEmail) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Комментарий не может быть пустым");
        }

        Task task = findTask(taskId);
        User author = getUser(authorEmail);

        validateTaskAccess(author, task, "COMMENT");

        Comment comment = Comment.builder()
                .task(task)
                .author(author)
                .content(request.getContent().trim())
                .build();

        Comment saved = commentRepository.save(comment);

        // === УВЕДОМЛЕНИЕ: Новый комментарий ===
        // Уведомляем всех участников задачи (создатель + исполнители), кроме автора
        String commentMsg = author.getFullName() + " оставил комментарий к задаче: \""
                + task.getTitle() + "\"";

        try {
            notificationService.notifyTaskParticipants(task, "NEW_COMMENT", commentMsg, author);
        } catch (RuntimeException ignored) {
            // Не блокируем сохранение комментария при сбое уведомления
        }

        // === УВЕДОМЛЕНИЕ: Упоминание (@mention) ===
        // Упомянутые получают отдельное уведомление (даже если уже получили выше)
        saveMentions(saved, request.getMentionUserIds(), author);

        return toResponse(saved);
    }

    // =========================================================
    // 3. Редактирование комментария — только ADMIN
    // =========================================================
    public CommentResponse updateComment(Long commentId, CommentUpdateRequest request, String email) {
        User user = getUser(email);

        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Редактировать комментарии может только администратор");
        }

        Comment comment = getComment(commentId);

        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Комментарий не может быть пустым");
        }

        comment.setContent(request.getContent().trim());
        Comment saved = commentRepository.save(comment);

        commentMentionRepository.deleteByCommentId(saved.getId());
        saveMentions(saved, request.getMentionUserIds(), user);

        return toResponse(saved);
    }

    // =========================================================
    // 4. Удаление комментария — только ADMIN
    // =========================================================
    public void deleteComment(Long commentId, String email) {
        User user = getUser(email);

        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Удалять комментарии может только администратор");
        }

        Comment comment = getComment(commentId);
        commentRepository.delete(comment);
    }

    // =========================================================
    // 5. Сохранение mention + уведомление
    // Упомянутые пользователи получают уведомление "MENTION"
    // Автор комментария не получает уведомление о своём же упоминании
    // =========================================================
    private void saveMentions(Comment comment, List<Long> mentionUserIds, User author) {
        if (mentionUserIds == null || mentionUserIds.isEmpty())
            return;

        for (Long userId : mentionUserIds) {
            User mentionedUser = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Упомянутый пользователь не найден: " + userId));

            CommentMention mention = CommentMention.builder()
                    .comment(comment)
                    .mentionedUser(mentionedUser)
                    .build();

            commentMentionRepository.save(mention);

            // Не уведомляем самого себя
            if (!mentionedUser.getId().equals(author.getId())) {
                notificationService.send(
                        mentionedUser,
                        "MENTION",
                        author.getFullName() + " упомянул вас в комментарии к задаче: \""
                                + comment.getTask().getTitle() + "\"",
                        comment.getTask());
            }
        }
    }

    // =========================================================
    // 6. Проверка доступа к задаче для комментариев
    // =========================================================
    private void validateTaskAccess(User user, Task task, String action) {
        Role role = user.getRole();

        if (role == Role.ADMIN)
            return;

        if (role == Role.MANAGER) {
            if (task.getProject() == null ||
                    task.getProject().getDepartment() == null ||
                    task.getProject().getDepartment().getManager() == null ||
                    !task.getProject().getDepartment().getManager().getId().equals(user.getId())) {
                throw new AccessDeniedException("Нет доступа к комментариям задач другого отдела");
            }
            return;
        }

        if (role == Role.PM) {
            if (task.getProject() == null ||
                    task.getProject().getPm() == null ||
                    !task.getProject().getPm().getId().equals(user.getId())) {
                throw new AccessDeniedException(
                        "ПМ может работать только с комментариями задач своего проекта");
            }
            return;
        }

        if (role == Role.TEAM) {
            boolean assigned = task.getAssignees() != null &&
                    task.getAssignees().stream()
                            .map(TaskAssignee::getUser)
                            .anyMatch(u -> u != null && u.getId().equals(user.getId()));

            if (!assigned) {
                throw new AccessDeniedException(
                        "Команда может работать только с комментариями своих задач");
            }
            return;
        }

        throw new AccessDeniedException("Недостаточно прав");
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }

    private Task findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Задача не найдена"));
    }

    private Comment getComment(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Комментарий не найден"));
    }

    private CommentResponse toResponse(Comment comment) {
        CommentResponse r = new CommentResponse();

        r.setId(comment.getId());
        r.setContent(comment.getContent());
        r.setCreatedAt(comment.getCreatedAt());
        r.setUpdatedAt(comment.getUpdatedAt());

        if (comment.getTask() != null) {
            r.setTaskId(comment.getTask().getId());
        }

        if (comment.getAuthor() != null) {
            r.setAuthorId(comment.getAuthor().getId());
            r.setAuthorName(comment.getAuthor().getFullName());
        }

        List<CommentMention> mentions = commentMentionRepository.findByCommentId(comment.getId());

        r.setMentionUserIds(
                mentions.stream()
                        .map(m -> m.getMentionedUser().getId())
                        .toList());

        r.setMentionUserNames(
                mentions.stream()
                        .map(m -> m.getMentionedUser().getFullName())
                        .toList());

        return r;
    }
}

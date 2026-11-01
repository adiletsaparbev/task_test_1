package com.example.demo.services;

import com.example.demo.dto.response.AnalyticsResponse;
import com.example.demo.dto.response.EmployeePerformanceResponse;
import com.example.demo.dto.response.TaskHistoryResponse;
import com.example.demo.entity.*;
import com.example.demo.enums.Role;
import com.example.demo.enums.TaskStatus;
import com.example.demo.exception.AccessDeniedException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final DepartmentRepository departmentRepository;

    // =========================================================
    // Полная аналитика: статусы + приоритеты + сотрудники
    // ADMIN — по всем задачам или по конкретному проекту
    // MANAGER — только задачи своего отдела
    // =========================================================
    public AnalyticsResponse getAnalytics(String email, Long projectId, Long departmentId) {
        User currentUser = getUser(email);

        List<Task> tasks = getTasksForUser(currentUser, projectId, departmentId);

        AnalyticsResponse response = new AnalyticsResponse();

        // 1. По статусам
        response.setTasksByStatus(
                tasks.stream().collect(
                        Collectors.groupingBy(
                                t -> t.getStatus() != null ? t.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        )
                )
        );

        // 2. По приоритетам
        response.setTasksByPriority(
                tasks.stream().collect(
                        Collectors.groupingBy(
                                t -> t.getPriority() != null ? t.getPriority().name() : "UNKNOWN",
                                Collectors.counting()
                        )
                )
        );

        // 3. Производительность сотрудников
        response.setEmployeePerformance(
                getPerformanceForTasks(tasks, currentUser, departmentId)
        );

        return response;
    }

    // =========================================================
    // Только статусы — для Kanban-бейджей
    // =========================================================
    public Map<String, Long> getTasksByStatus(String email, Long projectId) {
        User currentUser = getUser(email);
        List<Task> tasks = getTasksForUser(currentUser, projectId, null);

        return tasks.stream().collect(
                Collectors.groupingBy(
                        t -> t.getStatus() != null ? t.getStatus().name() : "UNKNOWN",
                        Collectors.counting()
                )
        );
    }

    // =========================================================
    // Только приоритеты
    // =========================================================
    public Map<String, Long> getTasksByPriority(String email, Long projectId) {
        User currentUser = getUser(email);
        List<Task> tasks = getTasksForUser(currentUser, projectId, null);

        return tasks.stream().collect(
                Collectors.groupingBy(
                        t -> t.getPriority() != null ? t.getPriority().name() : "UNKNOWN",
                        Collectors.counting()
                )
        );
    }

    // =========================================================
    // Производительность сотрудников отдельно
    // =========================================================
    public List<EmployeePerformanceResponse> getEmployeePerformance(
            String email, Long departmentId) {
        User currentUser = getUser(email);
        List<Task> tasks = getTasksForUser(currentUser, null, departmentId);
        return getPerformanceForTasks(tasks, currentUser, departmentId);
    }

    // =========================================================
    // История изменений задачи
    // Доступ: тот же что и к задаче (по роли)
    // =========================================================
    public List<TaskHistoryResponse> getTaskHistory(String email, Long taskId) {
        User currentUser = getUser(email);

        // Проверяем доступ к задаче
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Задача не найдена"));

        checkTaskAccess(currentUser, task);

        return taskHistoryRepository.findByTaskIdOrderByChangedAtAsc(taskId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    // =========================================================
    // Внутренние методы
    // =========================================================

    // Получить задачи в зависимости от роли и фильтров
    private List<Task> getTasksForUser(User user, Long projectId, Long departmentId) {
        List<Task> all;

        if (user.getRole() == Role.ADMIN) {
            all = projectId != null
                    ? taskRepository.findByProjectId(projectId)
                    : taskRepository.findAllWithRelations();
        } else if (user.getRole() == Role.MANAGER) {
            // Менеджер — только задачи своего отдела
            Department dept = departmentRepository.findFirstByManagerId(user.getId())
                    .orElseThrow(() -> new AccessDeniedException(
                            "У руководителя нет назначенного отдела"));
            all = taskRepository.findByDepartmentId(dept.getId());
        } else {
            throw new AccessDeniedException("У вас нет доступа к аналитике");
        }

        // Фильтр по отделу (только для ADMIN)
        if (departmentId != null && user.getRole() == Role.ADMIN) {
            all = all.stream()
                    .filter(t -> t.getProject() != null
                            && t.getProject().getDepartment() != null
                            && t.getProject().getDepartment().getId().equals(departmentId))
                    .toList();
        }

        return all;
    }

    // Считаем производительность по переданному списку задач
    private List<EmployeePerformanceResponse> getPerformanceForTasks(
            List<Task> tasks, User currentUser, Long departmentId) {

        // Собираем всех участников из этих задач (исполнители)
        List<User> employees;

        if (departmentId != null) {
            employees = userRepository.findByDepartmentId(departmentId).stream()
                    .filter(u -> u.getRole() == Role.PM || u.getRole() == Role.TEAM)
                    .toList();
        } else if (currentUser.getRole() == Role.MANAGER) {
            Department dept = departmentRepository.findFirstByManagerId(currentUser.getId())
                    .orElse(null);
            if (dept == null) return List.of();
            employees = userRepository.findByDepartmentId(dept.getId()).stream()
                    .filter(u -> u.getRole() == Role.PM || u.getRole() == Role.TEAM)
                    .toList();
        } else {
            // ADMIN без фильтра — берём всех участников из задач
            employees = tasks.stream()
                    .flatMap(t -> t.getAssignees() != null
                            ? t.getAssignees().stream().map(TaskAssignee::getUser)
                            : java.util.stream.Stream.empty())
                    .filter(u -> u != null)
                    .distinct()
                    .toList();
        }

        LocalDate today = LocalDate.now();

        return employees.stream().map(emp -> {
            // Задачи, где этот пользователь — исполнитель, из нашего набора tasks
            List<Task> assigned = tasks.stream()
                    .filter(t -> t.getAssignees() != null && t.getAssignees().stream()
                            .anyMatch(a -> a.getUser() != null
                                    && a.getUser().getId().equals(emp.getId())))
                    .toList();

            long completed = assigned.stream()
                    .filter(t -> t.getStatus() == TaskStatus.DONE)
                    .count();

            long overdue = assigned.stream()
                    .filter(t -> t.getDueDate() != null
                            && t.getDueDate().isBefore(today.atStartOfDay())
                            && t.getStatus() != TaskStatus.DONE)
                    .count();

            long inProgress = assigned.stream()
                    .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                    .count();

            EmployeePerformanceResponse r = new EmployeePerformanceResponse();
            r.setUserId(emp.getId());
            r.setFullName(emp.getFullName());
            r.setRole(emp.getRole().name());
            r.setDepartmentName(emp.getDepartment() != null
                    ? emp.getDepartment().getName() : null);
            r.setTotalAssigned(assigned.size());
            r.setCompletedTasks(completed);
            r.setOverdueTasks(overdue);
            r.setInProgressTasks(inProgress);

            return r;
        }).toList();
    }

    // Проверка доступа к задаче (для истории)
    private void checkTaskAccess(User user, Task task) {
        if (user.getRole() == Role.ADMIN) return;

        if (user.getRole() == Role.MANAGER) {
            if (task.getProject() == null
                    || task.getProject().getDepartment() == null
                    || task.getProject().getDepartment().getManager() == null
                    || !task.getProject().getDepartment().getManager().getId().equals(user.getId())) {
                throw new AccessDeniedException("Нет доступа к истории задач другого отдела");
            }
            return;
        }

        if (user.getRole() == Role.PM) {
            if (task.getProject() == null
                    || task.getProject().getPm() == null
                    || !task.getProject().getPm().getId().equals(user.getId())) {
                throw new AccessDeniedException("ПМ видит историю только задач своего проекта");
            }
            return;
        }

        if (user.getRole() == Role.TEAM) {
            boolean assigned = task.getAssignees() != null &&
                    task.getAssignees().stream()
                            .anyMatch(a -> a.getUser() != null
                                    && a.getUser().getId().equals(user.getId()));
            if (!assigned) {
                throw new AccessDeniedException("Нет доступа к истории этой задачи");
            }
            return;
        }

        throw new AccessDeniedException("Недостаточно прав");
    }

    private TaskHistoryResponse toHistoryResponse(TaskHistory h) {
        TaskHistoryResponse r = new TaskHistoryResponse();
        r.setId(h.getId());
        r.setFieldName(h.getFieldName());
        r.setOldValue(h.getOldValue());
        r.setNewValue(h.getNewValue());
        r.setChangedAt(h.getChangedAt());

        if (h.getChangedBy() != null) {
            r.setChangedById(h.getChangedBy().getId());
            r.setChangedByName(h.getChangedBy().getFullName());
        }

        return r;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }
}

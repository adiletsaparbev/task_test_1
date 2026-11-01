package com.example.demo.services;

import com.example.demo.dto.response.EmployeeProjectDTO;
import com.example.demo.dto.response.EmployeeTaskDTO;
import com.example.demo.dto.response.EmployeeWorkloadDTO;
import com.example.demo.dto.response.EmployeeWorkloadDetailsDTO;
import com.example.demo.entity.Department;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskAssignee;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.enums.TaskStatus;
import com.example.demo.exception.AccessDeniedException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repositories.DepartmentRepository;
import com.example.demo.repositories.TaskAssigneeRepository;
import com.example.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeWorkloadService {

    /**
     * Базовая емкость сотрудника (очки в единицах веса задач).
     * LOW=1, MEDIUM=2, HIGH=3, URGENT=5 — итого 10 очков = 100% загрузка.
     */
    private static final int DEFAULT_CAPACITY_POINTS = 10;

    private final UserRepository userRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final DepartmentRepository departmentRepository;

    public List<EmployeeWorkloadDTO> getEmployeesWorkload(String requesterEmail) {
        User requester = getUserByEmail(requesterEmail);
        List<User> visibleEmployees = resolveVisibleEmployeesForWorkload(requester);

        if (visibleEmployees.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = visibleEmployees.stream().map(User::getId).toList();
        List<TaskAssignee> assignments = taskAssigneeRepository.findByUserIdsWithTaskAndProject(userIds);

        Map<Long, List<TaskAssignee>> assignmentsByUserId = assignments.stream()
                .collect(Collectors.groupingBy(a -> a.getUser().getId()));

        Map<Long, String> managerDepartments = buildManagerDepartmentMap();

        return visibleEmployees.stream()
                .map(user -> {
                    List<TaskAssignee> userAssignments = assignmentsByUserId.getOrDefault(user.getId(), List.of());
                    // Дедупликация задач по taskId для корректной статистики
                    List<TaskAssignee> dedupedAssignments = userAssignments.stream()
                            .collect(Collectors.toMap(
                                    a -> a.getTask().getId(),
                                    a -> a,
                                    (a1, a2) -> a1))
                            .values().stream().toList();
                    Stats stats = computeStats(dedupedAssignments);

                    EmployeeWorkloadDTO dto = new EmployeeWorkloadDTO();
                    dto.setId(user.getId());
                    dto.setName(user.getFullName());
                    dto.setPosition(user.getRole() != null ? user.getRole().name() : null);
                    dto.setDepartment(resolveDepartmentName(user, managerDepartments));
                    dto.setTotalTasks(stats.totalTasks());
                    dto.setActiveTasks(stats.activeTasks());
                    dto.setCompletedTasks(stats.completedTasks());
                    dto.setOverdueTasks(stats.overdueTasks());
                    dto.setWorkloadPercent(stats.workloadPercent());
                    dto.setWorkloadStatus(resolveWorkloadStatus(stats.workloadPercent()));
                    return dto;
                })
                .sorted(Comparator.comparing(EmployeeWorkloadDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // Единая логика видимости для страницы загруженности
    // ADMIN: все активные сотрудники
    // MANAGER: только сотрудники своего отдела
    // PM: только сотрудники своего отдела
    private List<User> resolveVisibleEmployeesForWorkload(User requester) {
        if (requester.getRole() == null) {
            throw new AccessDeniedException("Роль пользователя не определена");
        }

        if (requester.getRole() == Role.ADMIN) {
            return userRepository.findAllActiveWithDepartment();
        }

        if (requester.getRole() == Role.MANAGER) {
            return resolveManagerEmployees(requester);
        }

        if (requester.getRole() == Role.PM) {
            return resolvePmEmployeesByDepartment(requester);
        }

        throw new AccessDeniedException("Недостаточно прав для просмотра загруженности сотрудников");
    }

    public EmployeeWorkloadDetailsDTO getEmployeeWorkloadDetails(Long employeeId, String requesterEmail) {
        User requester = getUserByEmail(requesterEmail);
        List<User> visibleEmployees = resolveVisibleEmployeesForWorkload(requester);

        User employee = visibleEmployees.stream()
                .filter(u -> u.getId().equals(employeeId))
                .findFirst()
                .orElseGet(() -> userRepository.findById(employeeId)
                        .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден")));

        boolean hasAccess = visibleEmployees.stream().anyMatch(u -> u.getId().equals(employee.getId()));
        if (!hasAccess) {
            throw new AccessDeniedException("Нет доступа к загруженности этого сотрудника");
        }

        List<TaskAssignee> rawAssignments = taskAssigneeRepository.findByUserIdWithTaskAndProject(employee.getId());
        // Дедупликация: одна задача считается ровно один раз
        List<TaskAssignee> assignments = rawAssignments.stream()
                .collect(Collectors.toMap(
                        a -> a.getTask().getId(),
                        a -> a,
                        (a1, a2) -> a1))
                .values().stream().toList();
        Stats stats = computeStats(assignments);

        Map<Long, String> managerDepartments = buildManagerDepartmentMap();

        EmployeeWorkloadDetailsDTO details = new EmployeeWorkloadDetailsDTO();

        EmployeeWorkloadDetailsDTO.Employee employeeBlock = new EmployeeWorkloadDetailsDTO.Employee();
        employeeBlock.setId(employee.getId());
        employeeBlock.setName(employee.getFullName());
        employeeBlock.setPosition(employee.getRole() != null ? employee.getRole().name() : null);
        employeeBlock.setDepartment(resolveDepartmentName(employee, managerDepartments));
        employeeBlock.setRole(employee.getRole() != null ? employee.getRole().name() : null);
        details.setEmployee(employeeBlock);

        EmployeeWorkloadDetailsDTO.Statistics statistics = new EmployeeWorkloadDetailsDTO.Statistics();
        statistics.setTotalTasks(stats.totalTasks());
        statistics.setActiveTasks(stats.activeTasks());
        statistics.setCompletedTasks(stats.completedTasks());
        statistics.setOverdueTasks(stats.overdueTasks());
        statistics.setWorkloadPercent(stats.workloadPercent());
        statistics.setWorkloadStatus(resolveWorkloadStatus(stats.workloadPercent()));
        details.setStatistics(statistics);

        EmployeeWorkloadDetailsDTO.TaskStatusStats taskStatusStats = new EmployeeWorkloadDetailsDTO.TaskStatusStats();
        taskStatusStats.setActive(stats.activeTasks());
        taskStatusStats.setCompleted(stats.completedTasks());
        taskStatusStats.setOverdue(stats.overdueTasks());
        details.setTaskStatusStats(taskStatusStats);

        Map<Long, EmployeeProjectDTO> projectMap = new LinkedHashMap<>();
        List<EmployeeTaskDTO> tasks = new ArrayList<>();

        for (TaskAssignee assignee : assignments) {
            Task task = assignee.getTask();
            if (task == null) {
                continue;
            }

            if (task.getProject() != null) {
                projectMap.putIfAbsent(
                        task.getProject().getId(),
                        new EmployeeProjectDTO(task.getProject().getId(), task.getProject().getName()));
            }

            EmployeeTaskDTO taskDto = new EmployeeTaskDTO();
            taskDto.setId(task.getId());
            taskDto.setTitle(task.getTitle());
            taskDto.setProject(task.getProject() != null ? task.getProject().getName() : null);
            taskDto.setPriority(task.getPriority() != null ? task.getPriority().name() : null);
            taskDto.setStatus(task.getStatus() != null ? task.getStatus().name() : null);
            taskDto.setDeadline(task.getDueDate());
            taskDto.setOverdue(isTaskOverdue(task));
            tasks.add(taskDto);
        }

        tasks = tasks.stream()
                .collect(Collectors.toMap(EmployeeTaskDTO::getId, t -> t, (left, right) -> left, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator
                        .comparing(EmployeeTaskDTO::getDeadline,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EmployeeTaskDTO::getId))
                .toList();

        details.setProjects(new ArrayList<>(projectMap.values()));
        details.setTasks(tasks);

        return details;
    }

    private List<User> resolveManagerEmployees(User manager) {
        Department department = departmentRepository.findFirstByManagerId(manager.getId())
                .orElseThrow(() -> new AccessDeniedException("Для руководителя не найден отдел"));

        List<User> users = new ArrayList<>(userRepository.findActiveByDepartmentIdWithDepartment(department.getId()));

        boolean containsManager = users.stream().anyMatch(u -> u.getId().equals(manager.getId()));
        if (!containsManager && manager.isActive()) {
            users.add(manager);
        }

        return users;
    }

    private List<User> resolvePmEmployeesByDepartment(User pm) {
        if (pm.getDepartment() == null || pm.getDepartment().getId() == null) {
            throw new AccessDeniedException("Для PM не найден отдел");
        }

        List<User> users = new ArrayList<>(
                userRepository.findActiveByDepartmentIdWithDepartment(pm.getDepartment().getId()));

        boolean containsPm = users.stream().anyMatch(u -> u.getId().equals(pm.getId()));
        if (!containsPm && pm.isActive()) {
            users.add(pm);
        }

        return users;
    }

    private Stats computeStats(List<TaskAssignee> assignments) {
        LocalDate today = LocalDate.now();

        List<Task> tasks = assignments.stream()
                .map(TaskAssignee::getTask)
                .filter(Objects::nonNull)
                .toList();

        long total = tasks.size();

        List<Task> activeTasks = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .toList();

        long active = activeTasks.size();

        long completed = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .count();

        long overdue = activeTasks.stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today.atStartOfDay()))
                .count();

        // capacity-based: sum(weight of active tasks) / DEFAULT_CAPACITY_POINTS * 100
        int activeWeightSum = activeTasks.stream()
                .mapToInt(this::calculateTaskWeight)
                .sum();

        double workloadPercent = Math.max(0.0,
                (activeWeightSum / (double) DEFAULT_CAPACITY_POINTS) * 100.0);

        return new Stats(total, active, completed, overdue, workloadPercent);
    }

    /**
     * Вес задачи по приоритету:
     * LOW=1, MEDIUM=2, HIGH=3, URGENT=5
     */
    int calculateTaskWeight(Task task) {
        if (task == null || task.getPriority() == null)
            return 1;
        return switch (task.getPriority()) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case URGENT -> 5;
        };
    }

    /**
     * Статус загруженности по проценту:
     * 10–50% → GREEN (низкая)
     * 50–80% → YELLOW (средняя)
     * 80–100%+ → RED (высокая)
     */
    String resolveWorkloadStatus(double workloadPercent) {
        if (workloadPercent < 50)
            return "GREEN";
        if (workloadPercent < 80)
            return "YELLOW";
        return "RED";
    }

    private String resolveDepartmentName(User user, Map<Long, String> managerDepartments) {
        if (user.getDepartment() != null) {
            return user.getDepartment().getName();
        }

        if (user.getRole() == Role.MANAGER) {
            return managerDepartments.get(user.getId());
        }

        return null;
    }

    private Map<Long, String> buildManagerDepartmentMap() {
        return departmentRepository.findAll().stream()
                .filter(d -> d.getManager() != null)
                .collect(Collectors.toMap(d -> d.getManager().getId(), Department::getName, (a, b) -> a, HashMap::new));
    }

    private boolean isTaskOverdue(Task task) {
        return task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDateTime.now())
                && task.getStatus() != TaskStatus.DONE;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }

    private record Stats(long totalTasks, long activeTasks, long completedTasks, long overdueTasks,
            double workloadPercent) {
    }
}

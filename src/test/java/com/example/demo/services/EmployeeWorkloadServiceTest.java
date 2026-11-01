package com.example.demo.services;

import com.example.demo.dto.response.EmployeeWorkloadDTO;
import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskAssignee;
import com.example.demo.entity.User;
import com.example.demo.enums.Priority;
import com.example.demo.enums.Role;
import com.example.demo.enums.TaskStatus;
import com.example.demo.repositories.DepartmentRepository;
import com.example.demo.repositories.TaskAssigneeRepository;
import com.example.demo.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeWorkloadServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskAssigneeRepository taskAssigneeRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private EmployeeWorkloadService service;

    private User adminUser;
    private User employee;
    private Project project;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .fullName("Admin User")
                .email("admin@test.com")
                .role(Role.ADMIN)
                .isActive(true)
                .build();

        employee = User.builder()
                .id(2L)
                .fullName("Employee A")
                .email("emp@test.com")
                .role(Role.TEAM)
                .isActive(true)
                .build();

        project = new Project();
        project.setId(10L);
        project.setName("Test Project");
    }

    // ──────────────────────────────────────────────
    // Тесты calculateTaskWeight
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Вес задачи LOW = 1")
    void calculateTaskWeight_low() {
        Task task = taskWithPriority(Priority.LOW);
        assertThat(service.calculateTaskWeight(task)).isEqualTo(1);
    }

    @Test
    @DisplayName("Вес задачи MEDIUM = 2")
    void calculateTaskWeight_medium() {
        Task task = taskWithPriority(Priority.MEDIUM);
        assertThat(service.calculateTaskWeight(task)).isEqualTo(2);
    }

    @Test
    @DisplayName("Вес задачи HIGH = 3")
    void calculateTaskWeight_high() {
        Task task = taskWithPriority(Priority.HIGH);
        assertThat(service.calculateTaskWeight(task)).isEqualTo(3);
    }

    @Test
    @DisplayName("Вес задачи URGENT = 5")
    void calculateTaskWeight_urgent() {
        Task task = taskWithPriority(Priority.URGENT);
        assertThat(service.calculateTaskWeight(task)).isEqualTo(5);
    }

    @Test
    @DisplayName("Вес задачи с null priority = 1 (по умолчанию)")
    void calculateTaskWeight_nullPriority() {
        Task task = new Task();
        task.setPriority(null);
        assertThat(service.calculateTaskWeight(task)).isEqualTo(1);
    }

    // ──────────────────────────────────────────────
    // Тесты resolveWorkloadStatus
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("0% → GREEN")
    void resolveStatus_zero() {
        assertThat(service.resolveWorkloadStatus(0.0)).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("49% → GREEN")
    void resolveStatus_49() {
        assertThat(service.resolveWorkloadStatus(49.0)).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("50% → YELLOW")
    void resolveStatus_50() {
        assertThat(service.resolveWorkloadStatus(50.0)).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("79% → YELLOW")
    void resolveStatus_79() {
        assertThat(service.resolveWorkloadStatus(79.0)).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("80% → ORANGE")
    void resolveStatus_80() {
        assertThat(service.resolveWorkloadStatus(80.0)).isEqualTo("ORANGE");
    }

    @Test
    @DisplayName("100% → ORANGE")
    void resolveStatus_100() {
        assertThat(service.resolveWorkloadStatus(100.0)).isEqualTo("ORANGE");
    }

    @Test
    @DisplayName(">100% → RED (перегрузка)")
    void resolveStatus_overload() {
        assertThat(service.resolveWorkloadStatus(150.0)).isEqualTo("RED");
    }

    // ──────────────────────────────────────────────
    // Интеграционные тесты через getEmployeesWorkload
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Сотрудник без задач → workload 0%, GREEN")
    void workload_noTasks() {
        mockAdminRequester();
        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of());
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        assertThat(result).hasSize(1);
        EmployeeWorkloadDTO dto = result.get(0);
        assertThat(dto.getWorkloadPercent()).isEqualTo(0.0);
        assertThat(dto.getWorkloadStatus()).isEqualTo("GREEN");
        assertThat(dto.getTotalTasks()).isEqualTo(0);
        assertThat(dto.getActiveTasks()).isEqualTo(0);
    }

    @Test
    @DisplayName("Одна LOW задача → 1/10*100 = 10%, GREEN")
    void workload_oneTaskLow() {
        mockAdminRequester();
        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of(assignee(1L, Priority.LOW, TaskStatus.IN_PROGRESS, null)));
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        EmployeeWorkloadDTO dto = result.get(0);
        assertThat(dto.getWorkloadPercent()).isEqualTo(10.0);
        assertThat(dto.getWorkloadStatus()).isEqualTo("GREEN");
        assertThat(dto.getTotalTasks()).isEqualTo(1);
        assertThat(dto.getActiveTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("Одна HIGH задача → 3/10*100 = 30%, GREEN")
    void workload_oneTaskHigh() {
        mockAdminRequester();
        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of(assignee(2L, Priority.HIGH, TaskStatus.IN_PROGRESS, null)));
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        assertThat(result.get(0).getWorkloadPercent()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Одна URGENT задача → 5/10*100 = 50%, YELLOW")
    void workload_oneTaskUrgent() {
        mockAdminRequester();
        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of(assignee(3L, Priority.URGENT, TaskStatus.IN_PROGRESS, null)));
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        EmployeeWorkloadDTO dto = result.get(0);
        assertThat(dto.getWorkloadPercent()).isEqualTo(50.0);
        assertThat(dto.getWorkloadStatus()).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("Несколько задач → корректная сумма весов (HIGH+MEDIUM+LOW = 3+2+1=6 → 60%, YELLOW)")
    void workload_multipleTasks_sumCorrect() {
        mockAdminRequester();
        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of(
                        assignee(10L, Priority.HIGH, TaskStatus.IN_PROGRESS, null),
                        assignee(11L, Priority.MEDIUM, TaskStatus.IN_PROGRESS, null),
                        assignee(12L, Priority.LOW, TaskStatus.IN_PROGRESS, null)));
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        EmployeeWorkloadDTO dto = result.get(0);
        assertThat(dto.getWorkloadPercent()).isEqualTo(60.0);
        assertThat(dto.getTotalTasks()).isEqualTo(3);
        assertThat(dto.getActiveTasks()).isEqualTo(3);
    }

    @Test
    @DisplayName("Перегрузка (>100%): 2*URGENT+HIGH = 5+5+3=13 → 130%, RED")
    void workload_overload_redStatus() {
        mockAdminRequester();
        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of(
                        assignee(20L, Priority.URGENT, TaskStatus.IN_PROGRESS, null),
                        assignee(21L, Priority.URGENT, TaskStatus.IN_PROGRESS, null),
                        assignee(22L, Priority.HIGH, TaskStatus.IN_PROGRESS, null)));
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        EmployeeWorkloadDTO dto = result.get(0);
        assertThat(dto.getWorkloadPercent()).isEqualTo(130.0);
        assertThat(dto.getWorkloadStatus()).isEqualTo("RED");
    }

    @Test
    @DisplayName("DONE задачи не учитываются в workload")
    void workload_completedTasksNotCounted() {
        mockAdminRequester();
        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of(
                        assignee(30L, Priority.URGENT, TaskStatus.DONE, null), // не считается
                        assignee(31L, Priority.LOW, TaskStatus.IN_PROGRESS, null) // считается: 1
                ));
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        EmployeeWorkloadDTO dto = result.get(0);
        assertThat(dto.getWorkloadPercent()).isEqualTo(10.0); // только LOW=1/10*100
        assertThat(dto.getTotalTasks()).isEqualTo(2);
        assertThat(dto.getCompletedTasks()).isEqualTo(1);
        assertThat(dto.getActiveTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("overdueTasks считается корректно")
    void workload_overdueTasks() {
        mockAdminRequester();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of(
                        assignee(40L, Priority.MEDIUM, TaskStatus.IN_PROGRESS, yesterday), // просрочена
                        assignee(41L, Priority.LOW, TaskStatus.IN_PROGRESS, tomorrow), // не просрочена
                        assignee(42L, Priority.HIGH, TaskStatus.DONE, yesterday) // done — не просрочена
                ));
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        EmployeeWorkloadDTO dto = result.get(0);
        assertThat(dto.getOverdueTasks()).isEqualTo(1);
        assertThat(dto.getActiveTasks()).isEqualTo(2);
        assertThat(dto.getCompletedTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("Дублирующийся TaskAssignee для одной задачи не завышает статистику")
    void workload_duplicateAssignee_deduplication() {
        mockAdminRequester();
        // Одна и та же задача (id=50) дважды в TaskAssignee
        TaskAssignee dup1 = assignee(50L, Priority.HIGH, TaskStatus.IN_PROGRESS, null);
        TaskAssignee dup2 = assignee(50L, Priority.HIGH, TaskStatus.IN_PROGRESS, null);

        when(taskAssigneeRepository.findByUserIdsWithTaskAndProject(List.of(2L)))
                .thenReturn(List.of(dup1, dup2));
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<EmployeeWorkloadDTO> result = service.getEmployeesWorkload("admin@test.com");

        EmployeeWorkloadDTO dto = result.get(0);
        assertThat(dto.getTotalTasks()).isEqualTo(1); // не 2
        assertThat(dto.getWorkloadPercent()).isEqualTo(30.0); // HIGH=3/10*100=30, не 60
    }

    // ──────────────────────────────────────────────
    // Вспомогательные методы
    // ──────────────────────────────────────────────

    private void mockAdminRequester() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(userRepository.findAllActiveWithDepartment()).thenReturn(List.of(employee));
    }

    private Task taskWithPriority(Priority priority) {
        Task task = new Task();
        task.setId((long) priority.ordinal() + 100);
        task.setPriority(priority);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setProject(project);
        return task;
    }

    private TaskAssignee assignee(Long taskId, Priority priority, TaskStatus status, LocalDate dueDate) {
        Task task = new Task();
        task.setId(taskId);
        task.setPriority(priority);
        task.setStatus(status);
        task.setDueDate(dueDate);
        task.setProject(project);

        TaskAssignee ta = new TaskAssignee();
        ta.setUser(employee);
        ta.setTask(task);
        return ta;
    }
}

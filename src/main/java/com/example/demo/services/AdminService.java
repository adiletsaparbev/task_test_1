package com.example.demo.services;

import com.example.demo.dto.record.*;
import com.example.demo.entity.Department;
import com.example.demo.entity.Project;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repositories.DepartmentRepository;
import com.example.demo.repositories.ProjectRepository;
import com.example.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;

    // --- УПРАВЛЕНИЕ ОТДЕЛАМИ ---
    public List<DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(d -> new DepartmentDto(
                        d.getId(),
                        d.getName(),
                        d.getManager() == null ? null
                                : new ManagerDto(
                                        d.getManager().getId(),
                                        d.getManager().getFullName(),
                                        d.getManager().getEmail(),
                                        d.getManager().getRole().name())))
                .toList();
    }

    public DepartmentDto createDepartment(Department dept) {
        Department saved = departmentRepository.save(dept);
        return toDto(saved);
    }

    public DepartmentDto updateDepartment(Long id, Department department) {
        Department existing = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // 1. Обновляем имя, если оно пришло
        if (department.getName() != null)
            existing.setName(department.getName());

        if (department.getManager() != null)
            existing.setManager(department.getManager());
        // 2. Обновляем менеджера, если пришел managerId
        if (department.getManager() != null && department.getManager().getId() != null) {
            User newManager = userRepository.findById(department.getManager().getId())
                    .orElseThrow(() -> new RuntimeException("Manager user not found"));

            // ПРОВЕРКА: является ли пользователь менеджером?
            if (newManager.getRole() != Role.MANAGER) {
                throw new IllegalArgumentException("Пользователь должен иметь роль MANAGER");
            }

            existing.setManager(newManager);
        }

        Department saved = departmentRepository.save(existing);
        return toDto(saved);
    }

    private DepartmentDto toDto(Department d) {
        User m = d.getManager();
        return new DepartmentDto(
                d.getId(),
                d.getName(),
                m == null ? null
                        : new ManagerDto(
                                m.getId(),
                                m.getFullName(),
                                m.getEmail(),
                                m.getRole().name()));
    }

    private UserDto toDto(User u) {
        Department d = u.getDepartment();
        return new UserDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole().name(),
                u.isActive(),
                d == null ? null : new DepartmentShortDto(d.getId(), d.getName()));
    }

    private ProjectDto toDto(Project p) {
        User pm = p.getPm();
        Department d = p.getDepartment();

        return new ProjectDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getStatus() == null ? null : p.getStatus(),
                pm == null ? null
                        : new UserShortDto(
                                pm.getId(),
                                pm.getFullName(),
                                pm.getEmail(),
                                pm.getRole().name(),
                                pm.isActive()),
                d == null ? null : new DepartmentShortDto(d.getId(), d.getName()));
    }

    public void deleteDepartment(Long id) {
        // 1. Снять department у всех пользователей этого отдела
        List<User> members = userRepository.findByDepartmentId(id);
        members.forEach(u -> u.setDepartment(null));
        userRepository.saveAll(members);

        // 2. Снять department у всех проектов этого отдела
        List<Project> projects = projectRepository.findByDepartmentId(id);
        projects.forEach(p -> p.setDepartment(null));
        projectRepository.saveAll(projects);

        // 3. Удалить отдел
        departmentRepository.deleteById(id);
    }

    // --- УПРАВЛЕНИЕ ПОЛЬЗОВАТЕЛЯМИ ---
    public List<UserDto> findUsers(String query, Role role, Long deptId) {
        // Если фильтры не заданы — возвращаем всех пользователей напрямую (безопасно)
        if (query == null && role == null && deptId == null) {
            return userRepository.findAll().stream()
                    .map(this::toDto)
                    .toList();
        }
        return userRepository.searchUsers(query, role, deptId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public UserDto createUser(UserCreateDto dto) {
        User u = new User();
        u.setFullName(dto.fullName());
        u.setEmail(dto.email());
        u.setPassword(passwordEncoder.encode(dto.password()));
        u.setRole(Role.valueOf(dto.role()));

        if (dto.departmentId() != null) {
            Department dep = departmentRepository.findById(dto.departmentId()).orElseThrow();
            u.setDepartment(dep);
        }

        u.setActive(dto.active() == null || dto.active());
        return toDto(userRepository.save(u));
    }

    public UserDto updateUser(Long id, UserUpdateDto dto) {
        User u = userRepository.findById(id).orElseThrow();

        if (dto.fullName() != null)
            u.setFullName(dto.fullName());
        if (dto.role() != null)
            u.setRole(Role.valueOf(dto.role()));

        if (Boolean.TRUE.equals(dto.clearDepartment())) {
            u.setDepartment(null);
        } else if (dto.departmentId() != null) {
            Department dep = departmentRepository.findById(dto.departmentId()).orElseThrow();
            u.setDepartment(dep);
        }

        if (dto.active() != null)
            u.setActive(dto.active());

        return toDto(userRepository.save(u));
    }

    public UserDto setUserStatus(Long id, boolean active) {
        User u = userRepository.findById(id).orElseThrow();
        u.setActive(active);
        return toDto(userRepository.save(u));
    }

    // --- УПРАВЛЕНИЕ ПРОЕКТАМИ ---
    public List<ProjectDto> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public ProjectDto createProject(ProjectCreateDto dto) {
        Project p = new Project();
        p.setName(dto.name());
        p.setDescription(dto.description());

        if (dto.status() != null) {
            p.setStatus(dto.status());
        }

        // Загружаем сущности
        User pm = userRepository.findById(dto.pmId())
                .orElseThrow(() -> new RuntimeException("PM not found"));
        Department dep = departmentRepository.findById(dto.departmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // Валидация
        validatePmAssignment(pm, dep);

        p.setPm(pm);
        p.setDepartment(dep);

        return toDto(projectRepository.save(p));
    }

    public ProjectDto getProjectById(Long id) {
        return toDto(projectRepository.findById(id).orElseThrow());
    }

    public ProjectDto updateProject(Long id, ProjectUpdateDto dto) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (dto.name() != null)
            p.setName(dto.name());
        if (dto.description() != null)
            p.setDescription(dto.description());
        if (dto.status() != null)
            p.setStatus(dto.status());

        // Если обновляется PM или Отдел, нужно проверить их совместимость
        if (dto.pmId() != null || dto.departmentId() != null) {

            User currentPm = (dto.pmId() != null)
                    ? userRepository.findById(dto.pmId()).orElseThrow()
                    : p.getPm();

            Department currentDep = (dto.departmentId() != null)
                    ? departmentRepository.findById(dto.departmentId()).orElseThrow()
                    : p.getDepartment();

            // Валидация
            validatePmAssignment(currentPm, currentDep);

            p.setPm(currentPm);
            p.setDepartment(currentDep);
        }

        return toDto(projectRepository.save(p));
    }

    private void validatePmAssignment(User pm, Department department) {
        // Если менеджера нет, проверять нечего (или решите, обязателен ли он)
        if (pm == null) {
            return;
        }

        // 1. Проверка роли
        if (pm.getRole() == null || !pm.getRole().equals(Role.PM)) {
            throw new IllegalArgumentException("Назначенный пользователь не является PM.");
        }

        // 2. Проверка соответствия отделов
        if (department != null) {
            if (pm.getDepartment() == null || !pm.getDepartment().getId().equals(department.getId())) {
                throw new IllegalArgumentException("PM должен принадлежать к тому же отделу, что и проект.");
            }
        }
    }

    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }
}

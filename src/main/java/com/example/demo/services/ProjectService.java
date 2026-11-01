package com.example.demo.services;

import com.example.demo.dto.request.ProjectRequest;
import com.example.demo.dto.response.ProjectResponse;
import com.example.demo.entity.*;
import com.example.demo.enums.EntityType;
import com.example.demo.enums.Role;
import com.example.demo.exception.AccessDeniedException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final AttachmentRepository attachmentRepository;
    private final ProjectMemberRepository projectMemberRepository; // добавлен

    public Project create(ProjectRequest request, String email) {
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        User pm = null;
        if (request.getPmId() != null) {
            pm = userRepository.findById(request.getPmId())
                    .orElseThrow(() -> new ResourceNotFoundException("ПМ не найден"));
        }

        Department department = null;

        if (currentUser.getRole() == Role.ADMIN) {
            if (request.getDepartmentId() != null) {
                department = departmentRepository.findById(request.getDepartmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Отдел не найден"));
            }
        } else if (currentUser.getRole() == Role.MANAGER) {
            department = departmentRepository.findFirstByManagerId(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("У руководителя нет назначенного департамента"));
        } else if (currentUser.getRole() == Role.PM) {
            department = currentUser.getDepartment();
            if (department == null) {
                throw new AccessDeniedException("У PM нет назначенного отдела");
            }
            pm = currentUser;
        } else {
            throw new AccessDeniedException("У вас нет прав на создание проекта");
        }

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .pm(pm)
                .department(department)
                .deadline(request.getDeadline())
                .build();

        Project saved = projectRepository.save(project);

        // ── Сохраняем участников ──────────────────────────────────────────
        saveMembers(saved, request.getMemberIds());

        return saved;
    }

    public List<Project> getAll(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        Department department = null;
        if (user.getRole() == Role.MANAGER) {
            department = departmentRepository.findFirstByManagerId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager не состоит в департаментах"));
        }

        return switch (user.getRole()) {
            case ADMIN -> projectRepository.findAll();
            case MANAGER -> projectRepository.findByDepartmentId(department.getId());
            case PM -> projectRepository.findByPmId(user.getId());
            case TEAM -> {
                Department dept = user.getDepartment();
                if (dept != null) {
                    yield projectRepository.findByDepartmentId(dept.getId());
                }
                yield projectRepository.findByMemberUserId(user.getId());
            }
        };
    }

    public Project getById(Long id, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Проект не найден: " + id));

        if (user.getRole() == Role.ADMIN) return p;

        if (user.getRole() == Role.MANAGER) {
            if (!Objects.equals(p.getDepartment().getManager().getId(), user.getId())) {
                throw new AccessDeniedException("У руководителя нет назначенного департамента");
            }
            return p;
        }

        if (user.getRole() == Role.PM) {
            if (p.getPm() == null || !p.getPm().getId().equals(user.getId())) {
                throw new AccessDeniedException("Нет доступа к чужому проекту");
            }
            return p;
        }

        if (user.getRole() == Role.TEAM) {
            Department dept = user.getDepartment();
            if (dept != null && p.getDepartment() != null
                    && dept.getId().equals(p.getDepartment().getId())) {
                return p;
            }
            boolean isMember = p.getMembers() != null &&
                    p.getMembers().stream()
                            .anyMatch(m -> m.getUser().getId().equals(user.getId()));
            if (!isMember) {
                throw new AccessDeniedException("Нет доступа к проекту");
            }
            return p;
        }

        throw new AccessDeniedException("У вас нет прав на просмотр проекта");
    }

    public Project update(Long id, ProjectRequest request, String email) {
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        Project project = getById(id, email);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            project.setName(request.getName().trim());
        }

        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }

        if (request.getDeadline() != null || (request.getName() != null)) {
            project.setDeadline(request.getDeadline());
        }

        if (currentUser.getRole() == Role.ADMIN) {
            if (request.getDepartmentId() != null) {
                Department department = departmentRepository.findById(request.getDepartmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Отдел не найден"));
                project.setDepartment(department);
            }
        }

        if (currentUser.getRole() == Role.MANAGER) {
            if (project.getDepartment() == null ||
                    project.getDepartment().getManager() == null ||
                    !project.getDepartment().getManager().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Вы можете редактировать только проекты своего департамента");
            }
        }

        if (request.getPmId() != null) {
            User pm = userRepository.findById(request.getPmId())
                    .orElseThrow(() -> new ResourceNotFoundException("ПМ не найден"));

            if (pm.getRole() != Role.PM) {
                throw new IllegalArgumentException("Назначенный пользователь должен иметь роль PM");
            }

            if (project.getDepartment() != null) {
                if (pm.getDepartment() == null ||
                        !pm.getDepartment().getId().equals(project.getDepartment().getId())) {
                    throw new IllegalArgumentException("PM должен принадлежать тому же департаменту, что и проект");
                }
            }

            project.setPm(pm);
        }

        Project saved = projectRepository.save(project);

        // ── Обновляем участников, если переданы ──────────────────────────
        if (request.getMemberIds() != null) {
            // Удаляем старых участников и добавляем новых
            projectMemberRepository.deleteByProjectId(saved.getId());
            saveMembers(saved, request.getMemberIds());
        }

        return saved;
    }

    public void delete(Long id) {
        List<Task> tasks = taskRepository.findByProjectId(id);

        for (Task task : tasks) {
            Long taskId = task.getId();
            notificationRepository.deleteAll(notificationRepository.findByTaskId(taskId));
            taskHistoryRepository.deleteAll(
                    taskHistoryRepository.findByTaskIdOrderByChangedAtAsc(taskId));
            attachmentRepository.deleteByEntityTypeAndEntityId(EntityType.TASK, taskId);
        }

        taskRepository.deleteAll(tasks);
        projectRepository.deleteById(id);
    }

    // ─── Вспомогательный метод сохранения участников ─────────────────────────

    private void saveMembers(Project project, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return;

        List<ProjectMember> members = memberIds.stream()
                .map(userId -> userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + userId)))
                .map(user -> ProjectMember.builder()
                        .project(project)
                        .user(user)
                        .build())
                .toList();

        projectMemberRepository.saveAll(members);
    }

    // ─── DTO обёртки ─────────────────────────────────────────────────────────

    public List<ProjectResponse> getAllAsResponse(String email) {
        return getAll(email).stream().map(this::toProjectResponse).toList();
    }

    public ProjectResponse getByIdAsResponse(Long id, String email) {
        return toProjectResponse(getById(id, email));
    }

    public ProjectResponse createAsResponse(ProjectRequest request, String email) {
        return toProjectResponse(create(request, email));
    }

    public ProjectResponse updateAsResponse(Long id, ProjectRequest request, String email) {
        return toProjectResponse(update(id, request, email));
    }

    private ProjectResponse toProjectResponse(Project p) {
        ProjectResponse r = new ProjectResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setStatus(p.getStatus() != null ? p.getStatus().name() : null);

        if (p.getPm() != null) {
            r.setPmId(p.getPm().getId());
            r.setPmName(p.getPm().getFullName());
        }

        if (p.getDepartment() != null) {
            r.setDepartmentId(p.getDepartment().getId());
            r.setDepartmentName(p.getDepartment().getName());
        }

        // ── Загружаем актуальных участников из БД ─────────────────────────
        // (p.getMembers() может быть устаревшим после saveMembers в той же транзакции)
        List<ProjectMember> members = projectMemberRepository.findByProjectId(p.getId());
        if (members != null && !members.isEmpty()) {
            r.setMemberIds(members.stream().map(m -> m.getUser().getId()).toList());
            r.setMemberNames(members.stream().map(m -> m.getUser().getFullName()).toList());
        } else {
            r.setMemberIds(Collections.emptyList());
            r.setMemberNames(Collections.emptyList());
        }

        r.setTaskCount((int) taskRepository.countByProjectId(p.getId()));
        r.setDeadline(p.getDeadline());
        return r;
    }
}
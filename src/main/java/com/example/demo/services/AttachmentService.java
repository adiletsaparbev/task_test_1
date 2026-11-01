package com.example.demo.services;

import com.example.demo.dto.response.AttachmentResponse;
import com.example.demo.entity.Attachment;
import com.example.demo.entity.Comment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskAssignee;
import com.example.demo.entity.User;
import com.example.demo.enums.EntityType;
import com.example.demo.enums.Role;
import com.example.demo.exception.AccessDeniedException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repositories.AttachmentRepository;
import com.example.demo.repositories.CommentRepository;
import com.example.demo.repositories.TaskRepository;
import com.example.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CommentRepository commentRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "application/zip",
            "application/x-rar-compressed"
    );

    private static final long MAX_SIZE = 10L * 1024 * 1024; // 10 MB

    // =========================================================
    // 1. Upload без привязки
    // =========================================================
    public AttachmentResponse upload(MultipartFile file, String uploaderEmail) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Файл слишком большой. Максимум 10 МБ");
        }

        String mime = file.getContentType();
        if (mime == null || !ALLOWED_TYPES.contains(mime)) {
            throw new IllegalArgumentException("Недопустимый тип файла: " + mime);
        }

        User uploader = userRepository.findByEmail(uploaderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        Path dir = Paths.get(uploadDir, "temp");
        Files.createDirectories(dir);

        String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = dir.resolve(uniqueName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Attachment attachment = Attachment.builder()
                .uploader(uploader)
                .fileName(file.getOriginalFilename())
                .filePath(filePath.toString())
                .fileSize(file.getSize())
                .mimeType(mime)
                .build();

        return toResponse(attachmentRepository.save(attachment));
    }

    // =========================================================
    // 2. Привязка к задаче или комменту
    // =========================================================
    public AttachmentResponse bindToEntity(Long attachmentId,
                                           EntityType entityType,
                                           Long entityId,
                                           String userEmail) throws IOException {

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Файл не найден"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = attachment.getUploader() != null
                && attachment.getUploader().getId().equals(user.getId());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("Нельзя привязать чужой файл");
        }

        // TEAM может прикреплять только к комментариям
        if (user.getRole() == Role.TEAM && entityType != EntityType.COMMENT) {
            throw new AccessDeniedException("Команда может прикреплять файлы только к комментариям");
        }

        checkEntityScope(user, entityType, entityId);

        // Перемещение файла из temp в папку сущности
        Path oldPath = Paths.get(attachment.getFilePath());
        Path newDir = Paths.get(uploadDir, entityType.name().toLowerCase(), entityId.toString());
        Files.createDirectories(newDir);

        String storedFileName = oldPath.getFileName().toString();
        Path newPath = newDir.resolve(storedFileName);

        if (Files.exists(oldPath)) {
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        }

        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setFilePath(newPath.toString());

        return toResponse(attachmentRepository.save(attachment));
    }

    // =========================================================
    // 3. Список файлов
    // =========================================================
    public List<AttachmentResponse> getAttachments(EntityType entityType, Long entityId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        checkEntityScope(user, entityType, entityId);

        return attachmentRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // 4. Скачать файл
    // =========================================================
    // Добавь этот record (удобно использовать фичи Java 21)
    public record FileDownloadDto(Resource resource, String fileName, String contentType) {}

    // =========================================================
    // 4. Скачать файл
    // =========================================================
    public FileDownloadDto download(Long attachmentId, String userEmail) throws IOException {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Файл не найден"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        if (attachment.getEntityType() == null || attachment.getEntityId() == null) {
            throw new AccessDeniedException("Файл ещё не привязан к сущности");
        }

        checkEntityScope(user, attachment.getEntityType(), attachment.getEntityId());

        Path path = Paths.get(attachment.getFilePath());
        Resource resource = new UrlResource(path.toUri());

        if (!resource.exists()) {
            throw new ResourceNotFoundException("Физический файл не найден");
        }

        // Возвращаем файл вместе с его оригинальным именем и типом
        return new FileDownloadDto(resource, attachment.getFileName(), attachment.getMimeType());
    }
    // =========================================================
    // 5. Удаление файла
    // ADMIN — любой
    // Остальные — только свой файл и по правилам сущности
    // TEAM — только свои файлы комментариев
    // =========================================================
    public void delete(Long attachmentId, String userEmail) throws IOException {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Файл не найден"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = attachment.getUploader() != null
                && attachment.getUploader().getId().equals(user.getId());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("Удалить файл может только администратор или его загрузивший");
        }

        // TEAM может удалять только свои файлы комментариев
        if (!isAdmin && user.getRole() == Role.TEAM) {
            if (attachment.getEntityType() != EntityType.COMMENT) {
                throw new AccessDeniedException("Команда может удалять только свои файлы комментариев");
            }
        }

        // если файл уже привязан — проверяем scope доступа
        if (attachment.getEntityType() != null && attachment.getEntityId() != null) {
            checkEntityScope(user, attachment.getEntityType(), attachment.getEntityId());
        }

        Files.deleteIfExists(Paths.get(attachment.getFilePath()));
        attachmentRepository.delete(attachment);
    }

    // =========================================================
    // 6. Scope check
    // Доступ к файлам зависит от доступа к задаче
    // =========================================================
    private void checkEntityScope(User user, EntityType entityType, Long entityId) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        Task task;

        if (entityType == EntityType.TASK) {
            task = taskRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Задача не найдена"));
        } else if (entityType == EntityType.COMMENT) {
            Comment comment = commentRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Комментарий не найден"));
            task = comment.getTask();
        } else {
            throw new IllegalArgumentException("Неподдерживаемый тип сущности: " + entityType);
        }

        checkTaskAccess(user, task);
    }

    private void checkTaskAccess(User user, Task task) {
        Role role = user.getRole();

        if (role == Role.ADMIN) {
            return;
        }

        if (role == Role.MANAGER) {
            if (task.getProject() == null ||
                    task.getProject().getDepartment() == null ||
                    task.getProject().getDepartment().getManager() == null ||
                    !task.getProject().getDepartment().getManager().getId().equals(user.getId())) {
                throw new AccessDeniedException("Нет доступа к файлам задач другого отдела");
            }
            return;
        }

        if (role == Role.PM) {
            if (task.getProject() == null ||
                    task.getProject().getPm() == null ||
                    !task.getProject().getPm().getId().equals(user.getId())) {
                throw new AccessDeniedException("ПМ может работать только с файлами задач своего проекта");
            }
            return;
        }

        if (role == Role.TEAM) {
            boolean assigned = task.getAssignees() != null &&
                    task.getAssignees().stream()
                            .map(TaskAssignee::getUser)
                            .anyMatch(u -> u != null && u.getId().equals(user.getId()));

            if (!assigned) {
                throw new AccessDeniedException("Команда может работать только с файлами своих задач");
            }
            return;
        }

        throw new AccessDeniedException("Недостаточно прав");
    }

    // =========================================================
    // 7. Mapper
    // =========================================================
    private AttachmentResponse toResponse(Attachment a) {
        AttachmentResponse r = new AttachmentResponse();
        r.setId(a.getId());
        r.setFileName(a.getFileName());
        r.setMimeType(a.getMimeType());
        r.setFileSize(a.getFileSize());
        r.setUploadedAt(a.getUploadedAt());

        if (a.getEntityType() != null) {
            r.setEntityType(a.getEntityType().name());
        }

        r.setEntityId(a.getEntityId());

        if (a.getUploader() != null) {
            r.setUploaderId(a.getUploader().getId());
            r.setUploaderName(a.getUploader().getFullName());
        }

        return r;
    }
}

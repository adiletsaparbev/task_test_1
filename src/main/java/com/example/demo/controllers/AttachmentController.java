package com.example.demo.controllers;

import com.example.demo.dto.response.AttachmentResponse;
import com.example.demo.enums.EntityType;
import com.example.demo.services.AttachmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    // Upload — все авторизованные
    @PostMapping("/api/attachments/upload")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<AttachmentResponse> upload(
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        return ResponseEntity.ok(
                attachmentService.upload(file, userDetails.getUsername())
        );
    }

    // Привязать к задаче — ADMIN / MANAGER / PM
    @PostMapping("/api/tasks/{taskId}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<AttachmentResponse> attachToTask(
            @PathVariable Long taskId,
            @RequestParam Long attachmentId,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        return ResponseEntity.ok(
                attachmentService.bindToEntity(
                        attachmentId,
                        EntityType.TASK,
                        taskId,
                        userDetails.getUsername()
                )
        );
    }

    // Привязать к комменту — все роли
    @PostMapping("/api/comments/{commentId}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<AttachmentResponse> attachToComment(
            @PathVariable Long commentId,
            @RequestParam Long attachmentId,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        return ResponseEntity.ok(
                attachmentService.bindToEntity(
                        attachmentId,
                        EntityType.COMMENT,
                        commentId,
                        userDetails.getUsername()
                )
        );
    }

    // Список файлов — доступ зависит от задачи
    @GetMapping("/api/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<AttachmentResponse>> list(
            @RequestParam EntityType entityType,
            @RequestParam Long entityId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                attachmentService.getAttachments(entityType, entityId, userDetails.getUsername())
        );
    }

    // Скачать файл — доступ зависит от задачи
    @GetMapping("/api/attachments/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        AttachmentService.FileDownloadDto downloadData = attachmentService.download(id, userDetails.getUsername());

        // Кодируем имя файла для корректной передачи по HTTP
        String encodedFileName = URLEncoder.encode(downloadData.fileName(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                // attachment - заставляет браузер скачать файл.
                // Если поменять на inline - браузер попытается открыть картинку/pdf прямо во вкладке
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, downloadData.contentType()) // Подставляем реальный MIME-тип
                .body(downloadData.resource());
    }

    // Удаление — ADMIN или uploader с учётом правил
    @DeleteMapping("/api/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<Void> delete(
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        attachmentService.delete(attachmentId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
package com.example.demo.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentResponse {
    private Long id;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String entityType;
    private Long entityId;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime uploadedAt;
}
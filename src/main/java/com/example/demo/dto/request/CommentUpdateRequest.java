package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CommentUpdateRequest {

    @NotBlank(message = "Комментарий не может быть пустым")
    private String content;

    private List<Long> mentionUserIds;
}
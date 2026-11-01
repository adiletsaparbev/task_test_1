package com.example.demo.dto.request;

import com.example.demo.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubtaskStatusRequest {

    @NotNull(message = "Статус обязателен")
    private TaskStatus status;
}
package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagCreateRequest {

    @NotBlank(message = "Название тега обязательно")
    private String name;

    // например #4472C4
    private String color;
}
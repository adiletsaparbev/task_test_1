package com.example.demo.dto.response;

import lombok.Data;

@Data
public class UserResponse {
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private Long departmentId;
    private String departmentName;
}
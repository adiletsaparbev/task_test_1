package com.example.demo.controllers;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.request.TaskRequest;
import com.example.demo.enums.Role;
import com.example.demo.enums.Priority;
import com.example.demo.enums.TaskStatus;
import com.example.demo.dto.response.JwtResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TaskController.
 * Tests critical task workflows and role-based access control.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String teamToken;

    @BeforeEach
    void setUp() throws Exception {
        // Create ADMIN user
        RegisterRequest adminRequest = new RegisterRequest();
        adminRequest.setFullName("Admin User");
        adminRequest.setEmail("admin@example.com");
        adminRequest.setPassword("admin123");
        adminRequest.setRole(Role.ADMIN);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isOk());

        // Login ADMIN
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setEmail("admin@example.com");
        adminLogin.setPassword("admin123");

        MvcResult adminResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JwtResponse adminJwt = objectMapper.readValue(
                adminResult.getResponse().getContentAsString(),
                JwtResponse.class);
        adminToken = adminJwt.getToken();

        // Create TEAM user
        RegisterRequest teamRequest = new RegisterRequest();
        teamRequest.setFullName("Team User");
        teamRequest.setEmail("team@example.com");
        teamRequest.setPassword("team123");
        teamRequest.setRole(Role.TEAM);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(teamRequest)))
                .andExpect(status().isOk());

        // Login TEAM
        LoginRequest teamLogin = new LoginRequest();
        teamLogin.setEmail("team@example.com");
        teamLogin.setPassword("team123");

        MvcResult teamResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(teamLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JwtResponse teamJwt = objectMapper.readValue(
                teamResult.getResponse().getContentAsString(),
                JwtResponse.class);
        teamToken = teamJwt.getToken();
    }

    // ─────────────────────────────────────────────────────
    // Task CRUD Happy Path
    // ─────────────────────────────────────────────────────

    @Test
    void shouldCreateTask_whenUserIsAdmin() throws Exception {
        // Given
        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setTitle("Test Task");
        taskRequest.setDescription("Test Description");
        taskRequest.setPriority(Priority.HIGH);
        taskRequest.setStatus(TaskStatus.NEW);
        taskRequest.setDueDate(LocalDate.now().plusDays(7));

        // When & Then
        mockMvc.perform(post("/api/tasks")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Task"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void shouldGetAllTasks_whenAuthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/tasks")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    // ─────────────────────────────────────────────────────
    // Role-Based Access Control
    // ─────────────────────────────────────────────────────

    @Test
    void shouldReturn403_whenTeamUserAccessesAdminEndpoint() throws Exception {
        // When: TEAM user tries to access admin-only endpoint (e.g., /api/admin/users)
        // Note: We'll test with a protected endpoint that requires ADMIN role

        // Then: Should return 403 Forbidden
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + teamToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminAccess_toAdminEndpoint() throws Exception {
        // When: ADMIN user accesses admin-only endpoint
        // Then: Should succeed (may return 200 or other non-403 status)
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn401_whenAccessingTasksWithoutToken() throws Exception {
        // When: No token provided
        // Then: Should return 401
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowTeamUserToViewTasks() throws Exception {
        // When: TEAM user views tasks (allowed by @PreAuthorize)
        // Then: Should succeed
        mockMvc.perform(get("/api/tasks")
                .header("Authorization", "Bearer " + teamToken))
                .andExpect(status().isOk());
    }
}

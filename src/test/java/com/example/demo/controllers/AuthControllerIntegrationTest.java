package com.example.demo.controllers;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.response.JwtResponse;
import com.example.demo.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController.
 * Tests critical auth flows: login happy path, registration, token generation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────
    // Login Happy Path
    // ─────────────────────────────────────────────────────

    @Test
    void shouldReturnJwtToken_whenLoginWithValidCredentials() throws Exception {
        // Given: Create a test user first
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setFullName("Test User");
        registerRequest.setEmail("testuser@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setRole(Role.ADMIN);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // When: Login with the same credentials
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("testuser@example.com");
        loginRequest.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.email").value("testuser@example.com"))
                .andExpect(jsonPath("$.role").value("TEAM"))
                .andReturn();

        // Then: Verify token is not empty
        String responseBody = result.getResponse().getContentAsString();
        JwtResponse jwtResponse = objectMapper.readValue(responseBody, JwtResponse.class);
        assertThat(jwtResponse.getToken()).isNotBlank();
        assertThat(jwtResponse.getToken().length()).isGreaterThan(50); // JWT должен быть длинным
    }

    @Test
    void shouldReturn401_whenLoginWithInvalidPassword() throws Exception {
        // Given: Create a test user
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setFullName("Another User");
        registerRequest.setEmail("anotheruser@example.com");
        registerRequest.setPassword("correctpassword");
        registerRequest.setRole(Role.TEAM);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // When: Try to login with wrong password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("anotheruser@example.com");
        loginRequest.setPassword("wrongpassword");

        // Then: Should return 401
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldReturn401_whenLoginWithNonExistentUser() throws Exception {
        // Given: User doesn't exist
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("nonexistent@example.com");
        loginRequest.setPassword("anypassword");

        // When: Try to login
        // Then: Should return 401
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────
    // Registration Tests
    // ─────────────────────────────────────────────────────

    @Test
    void shouldRegisterNewUser_withValidData() throws Exception {
        // Given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setFullName("New User");
        registerRequest.setEmail("newuser@example.com");
        registerRequest.setPassword("securepassword123");
        registerRequest.setRole(Role.TEAM);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("успешно")));
    }

    @Test
    void shouldReturn400_whenRegisterWithDuplicateEmail() throws Exception {
        // Given: Register first user
        RegisterRequest firstUser = new RegisterRequest();
        firstUser.setFullName("First User");
        firstUser.setEmail("duplicate@example.com");
        firstUser.setPassword("password123");
        firstUser.setRole(Role.TEAM);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstUser)))
                .andExpect(status().isOk());

        // When: Try to register with same email
        RegisterRequest secondUser = new RegisterRequest();
        secondUser.setFullName("Second User");
        secondUser.setEmail("duplicate@example.com");
        secondUser.setPassword("differentpassword");
        secondUser.setRole(Role.MANAGER);

        // Then: Should fail
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondUser)))
                .andExpect(status().isBadRequest());
    }
}

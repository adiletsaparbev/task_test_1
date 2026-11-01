package com.example.demo.security;

import com.example.demo.dto.request.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security integration tests for authentication, authorization, and CORS.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────
    // 401 Unauthorized Tests
    // ─────────────────────────────────────────────────────

    @Test
    void shouldReturn401_whenAccessingProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldReturn401_whenUsingInvalidToken() throws Exception {
        mockMvc.perform(get("/api/tasks")
                .header("Authorization", "Bearer invalid_token_here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401_whenTokenIsMalformed() throws Exception {
        mockMvc.perform(get("/api/tasks")
                .header("Authorization", "NotBearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────
    // Public Endpoints Tests
    // ─────────────────────────────────────────────────────

    @Test
    void shouldAllowAccess_toLoginEndpointWithoutToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("wrong_password");

        // Should NOT return 403, but may return 400/401 from auth logic
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().is(not(403))); // Not forbidden, may be 401 from bad credentials
    }

    @Test
    void shouldAllowAccess_toRegisterEndpointWithoutToken() throws Exception {
        // Should be accessible without token (may fail validation but not auth)
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is(not(403))); // Not forbidden
    }

    // ─────────────────────────────────────────────────────
    // CORS Tests
    // ─────────────────────────────────────────────────────

    @Test
    void shouldHandleCorsPreflightRequest() throws Exception {
        mockMvc.perform(options("/api/tasks")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void shouldIncludeCorsHeaders_inActualRequest() throws Exception {
        mockMvc.perform(get("/api/auth/login")
                .header("Origin", "http://localhost:5173"))
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    // ─────────────────────────────────────────────────────
    // Response Format Tests
    // ─────────────────────────────────────────────────────

    @Test
    void shouldReturnJsonError_when401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/projects"));
    }
}

package com.example.demo.security;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.filter.LoginRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционный тест для проверки rate limiting на login endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.rate-limit.login.max-attempts=3",
        "security.rate-limit.login.window-seconds=60"
})
class LoginRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginRateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        // Очистка rate limit между тестами
        rateLimitFilter.clearAllRateLimits();
    }

    @Test
    void shouldBlockAfterMaxAttempts() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("wrong_password");

        String requestBody = objectMapper.writeValueAsString(loginRequest);

        // Первые 3 попытки должны пройти (вернуть 401 или другой код, но НЕ 429)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().is(not(429))); // Не rate limit
        }

        // 4-я попытка должна вернуть 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.retryAfter").isNumber())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void shouldNotBlockOtherEndpoints() throws Exception {
        // Выполняем 10 запросов к другому endpoint - не должно быть блокировки
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().is(not(429))); // Может быть 400, но не 429
        }
    }

    @Test
    void shouldTrackDifferentIpsSeparately() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("wrong_password");

        String requestBody = objectMapper.writeValueAsString(loginRequest);

        // 3 попытки с IP1
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .header("X-Forwarded-For", "192.168.1.1"))
                    .andExpect(status().is(not(429)));
        }

        // IP1 заблокирован
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isTooManyRequests());

        // IP2 должен работать нормально (не заблокирован)
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("X-Forwarded-For", "192.168.1.2"))
                .andExpect(status().is(not(429)));
    }

    @Test
    void shouldRespectXForwardedForHeader() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("wrong_password");

        String requestBody = objectMapper.writeValueAsString(loginRequest);

        // 3 попытки с X-Forwarded-For
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .header("X-Forwarded-For", "10.0.0.100"))
                    .andExpect(status().is(not(429)));
        }

        // 4-я попытка заблокирована
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("X-Forwarded-For", "10.0.0.100"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    void shouldHandleMultipleIpsInXForwardedFor() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("wrong_password");

        String requestBody = objectMapper.writeValueAsString(loginRequest);

        // Первый IP в цепочке должен использоваться для rate limiting
        String forwardedFor = "203.0.113.1, 198.51.100.1, 192.0.2.1";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .header("X-Forwarded-For", forwardedFor))
                    .andExpect(status().is(not(429)));
        }

        // 4-я попытка с тем же первым IP - заблокирована
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("X-Forwarded-For", forwardedFor))
                .andExpect(status().isTooManyRequests());
    }
}

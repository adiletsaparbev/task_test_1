package com.example.demo.filter;

import com.example.demo.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit тест для LoginRateLimitFilter без поднятия Spring Context.
 */
class LoginRateLimitFilterUnitTest {

    private LoginRateLimitFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        // Используем package-private конструктор для тестирования
        // maxAttempts=5, windowSeconds=60
        filter = new LoginRateLimitFilter(5, 60);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);

        // Очистка rate limit перед каждым тестом
        filter.clearAllRateLimits();
    }

    @Test
    void shouldPassThrough_whenNotLoginEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/users");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldPassThrough_whenGetMethodOnLogin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldAllowFirstAttempts_thenBlock() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        // Первые 5 попыток должны пройти (max-attempts=5 по умолчанию)
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, filterChain);
            verify(filterChain, times(i + 1)).doFilter(request, response);
        }

        // 6-я попытка должна быть заблокирована
        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> filter.doFilter(request, response, filterChain));

        assertTrue(exception.getMessage().contains("Превышен лимит"));
        assertTrue(exception.getRetryAfterSeconds() > 0);
    }

    @Test
    void shouldTrackDifferentIps_independently() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");

        // IP1: 5 попыток
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, filterChain);
        }

        // IP1: 6-я попытка заблокирована
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        assertThrows(RateLimitExceededException.class,
                () -> filter.doFilter(request, response, filterChain));

        // IP2: первая попытка проходит
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));
    }

    @Test
    void shouldUseXForwardedFor_whenPresent() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // 5 попыток с X-Forwarded-For IP
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, filterChain);
        }

        // 6-я попытка заблокирована по X-Forwarded-For IP
        assertThrows(RateLimitExceededException.class,
                () -> filter.doFilter(request, response, filterChain));
    }

    @Test
    void shouldParseFirstIp_fromMultipleXForwardedFor() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.1, 192.0.2.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // 5 попыток
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, filterChain);
        }

        // Заблокировано по первому IP из цепочки
        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> filter.doFilter(request, response, filterChain));

        assertNotNull(exception.getMessage());
    }

    @Test
    void shouldUseXRealIp_whenNoXForwardedFor() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.50");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // 5 попыток с X-Real-IP
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, filterChain);
        }

        // 6-я попытка заблокирована по X-Real-IP
        assertThrows(RateLimitExceededException.class,
                () -> filter.doFilter(request, response, filterChain));
    }

    @Test
    void shouldClearAllRateLimits() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");

        // 5 попыток
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, filterChain);
        }

        // Очистка
        filter.clearAllRateLimits();

        // После очистки можно снова сделать 5 попыток
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, filterChain);
        }

        // Только 11-я попытка заблокируется
        assertThrows(RateLimitExceededException.class,
                () -> filter.doFilter(request, response, filterChain));
    }
}

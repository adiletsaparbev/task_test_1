package com.example.demo.filter;

import com.example.demo.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting filter для защиты login endpoint от brute force атак.
 * 
 * Стратегия: фиксированное окно (fixed window) с периодической очисткой.
 * Не требует Redis, подходит для single-instance или малых команд.
 * 
 * Ограничения:
 * - Работает только для одного instance (не distributed)
 * - Сбрасывается при рестарте приложения
 * - Для production с несколькими инстансами используйте Redis
 */
@Component
@Order(2) // После CorrelationIdFilter(1), до JwtFilter
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    @Value("${security.rate-limit.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${security.rate-limit.login.window-seconds:300}")
    private long windowSeconds;

    // IP -> (attempts counter, window start time)
    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    // Cleanup scheduler для предотвращения memory leak
    private final ScheduledExecutorService scheduler;

    public LoginRateLimitFilter() {
        this.maxAttempts = 5; // Default value
        this.windowSeconds = 300; // Default value

        // Cleanup scheduler
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RateLimitCleanup");
            thread.setDaemon(true);
            return thread;
        });

        // Очистка старых записей каждые 5 минут
        scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Package-private constructor для тестирования с кастомными параметрами.
     */
    LoginRateLimitFilter(int maxAttempts, long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RateLimitCleanup");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Rate limit только для login endpoint
        if (!requestPath.equals("/api/auth/login") || !request.getMethod().equals("POST")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        // Проверка и обновление rate limit
        RateLimitEntry entry = rateLimitMap.computeIfAbsent(clientIp, k -> new RateLimitEntry());

        Instant now = Instant.now();
        Instant windowStart = entry.getWindowStart();

        // Если окно истекло - сбросить счётчик
        if (Duration.between(windowStart, now).getSeconds() >= windowSeconds) {
            entry.reset(now);
        }

        // Проверка превышения лимита
        int currentAttempts = entry.getAttempts().get();
        if (currentAttempts >= maxAttempts) {
            long timeLeftSeconds = windowSeconds - Duration.between(windowStart, now).getSeconds();
            log.warn("[RATE_LIMIT] Blocked login attempt from IP: {} (attempts: {}/{})",
                    clientIp, currentAttempts, maxAttempts);

            throw new RateLimitExceededException(
                    "Превышен лимит попыток входа. Попробуйте позже.",
                    timeLeftSeconds);
        }

        // Увеличить счётчик попыток
        entry.getAttempts().incrementAndGet();

        log.debug("[RATE_LIMIT] Login attempt from IP: {} (attempts: {}/{})",
                clientIp, entry.getAttempts().get(), maxAttempts);

        filterChain.doFilter(request, response);
    }

    /**
     * Получение реального IP клиента с учётом reverse proxy.
     * Использует X-Forwarded-For если доступен.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For может содержать несколько IP через запятую
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Периодическая очистка истекших записей для предотвращения утечки памяти.
     */
    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        int removed = 0;

        for (Map.Entry<String, RateLimitEntry> entry : rateLimitMap.entrySet()) {
            long ageSeconds = Duration.between(entry.getValue().getWindowStart(), now).getSeconds();
            if (ageSeconds >= windowSeconds * 2) { // Удаляем записи старше двух окон
                rateLimitMap.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("[RATE_LIMIT] Cleaned up {} expired entries. Remaining: {}",
                    removed, rateLimitMap.size());
        }
    }

    /**
     * Вспомогательный класс для хранения состояния rate limit.
     */
    private static class RateLimitEntry {
        private final AtomicInteger attempts = new AtomicInteger(0);
        private volatile Instant windowStart = Instant.now();

        public AtomicInteger getAttempts() {
            return attempts;
        }

        public Instant getWindowStart() {
            return windowStart;
        }

        public void reset(Instant newStart) {
            attempts.set(0);
            windowStart = newStart;
        }
    }

    /**
     * Публичный метод для тестирования - очистка всех rate limit записей.
     */
    public void clearAllRateLimits() {
        rateLimitMap.clear();
        log.debug("[RATE_LIMIT] All rate limit entries cleared");
    }
}

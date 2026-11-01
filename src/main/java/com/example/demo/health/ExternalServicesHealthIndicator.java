package com.example.demo.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for external services.
 * Checks if critical external dependencies are available.
 */
@Component("externalServices")
public class ExternalServicesHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // Add checks for your external services here
        // For now, always return UP (placeholder)

        boolean telegramApiReachable = checkTelegramApi();
        boolean geminiApiReachable = checkGeminiApi();

        if (telegramApiReachable && geminiApiReachable) {
            return Health.up()
                    .withDetail("telegram", "reachable")
                    .withDetail("gemini", "reachable")
                    .build();
        }

        // Partial outage - still UP but with warnings
        return Health.up()
                .withDetail("telegram", telegramApiReachable ? "reachable" : "unavailable")
                .withDetail("gemini", geminiApiReachable ? "reachable" : "unavailable")
                .withDetail("status", "degraded")
                .build();
    }

    private boolean checkTelegramApi() {
        // TODO: Implement actual Telegram API ping if needed
        // For now, assume it's always available
        return true;
    }

    private boolean checkGeminiApi() {
        // TODO: Implement actual Gemini API ping if needed
        // For now, assume it's always available
        return true;
    }
}

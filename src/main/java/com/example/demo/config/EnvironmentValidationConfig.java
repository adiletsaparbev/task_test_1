package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * Валидация критичных переменных окружения при старте приложения.
 * Если обязательные переменные не заданы - приложение не стартует (fail-fast).
 */
@Configuration
public class EnvironmentValidationConfig {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${telegram.bot.enabled:false}")
    private boolean telegramEnabled;

    @Value("${telegram.bot.token:}")
    private String telegramToken;

    @Value("${voice.parse.provider:client}")
    private String voiceParseProvider;

    @Value("${voice.parse.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${cors.allowed-origins}")
    private String corsAllowedOrigins;

    @PostConstruct
    public void validateRequiredEnvironmentVariables() {
        StringBuilder errors = new StringBuilder();

        // JWT secret обязателен всегда
        if (!StringUtils.hasText(jwtSecret)) {
            errors.append("❌ JWT_SECRET is not set. Generate one using: openssl rand -base64 64\n");
        } else if (jwtSecret.length() < 32) {
            errors.append("❌ JWT_SECRET is too short (minimum 32 characters required)\n");
        }

        // DB password обязателен всегда
        if (!StringUtils.hasText(dbPassword)) {
            errors.append("❌ Database password is not set (PGPASSWORD, DB_PASSWORD, or SPRING_DATASOURCE_PASSWORD)\n");
        }

        // Telegram token обязателен если бот включен
        if (telegramEnabled && !StringUtils.hasText(telegramToken)) {
            errors.append("❌ TELEGRAM_BOT_TOKEN is not set but telegram.bot.enabled=true\n");
        }

        // CORS origins обязательны
        if (!StringUtils.hasText(corsAllowedOrigins)) {
            errors.append("❌ CORS_ALLOWED_ORIGINS is not set (frontend will be blocked)\n");
        } else if (corsAllowedOrigins.contains("*")) {
            errors.append("❌ CORS_ALLOWED_ORIGINS contains \"*\" which is NOT ALLOWED with credentials=true\n");
        }

        // Warnings (не блокируют запуск)
        StringBuilder warnings = new StringBuilder();

        // Gemini API key - только warning если используется gemini провайдер
        if ("gemini".equalsIgnoreCase(voiceParseProvider) && !StringUtils.hasText(geminiApiKey)) {
            warnings.append(
                    "⚠️  GEMINI_API_KEY is not set but voice.parse.provider=gemini (voice features will fail)\n");
        }

        if (errors.length() > 0) {
            String errorMessage = "\n" +
                    "╔════════════════════════════════════════════════════════════╗\n" +
                    "║  APPLICATION STARTUP FAILED: Missing required environment  ║\n" +
                    "╚════════════════════════════════════════════════════════════╝\n\n" +
                    errors.toString() +
                    "\n📝 See .env.example for all available configuration options\n" +
                    "💡 For local development, use: spring.profiles.active=dev\n";

            throw new IllegalStateException(errorMessage);
        }

        // Print warnings but don't fail
        if (warnings.length() > 0) {
            System.out.println("\n⚠️  WARNINGS:\n" + warnings.toString());
        }

        // Success
        System.out.println("✅ All required environment variables are set");
    }
}

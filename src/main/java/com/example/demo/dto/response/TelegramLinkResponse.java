package com.example.demo.dto.response;

import java.time.LocalDateTime;

/**
 * DTO для ответа на запрос создания Telegram deep-link.
 * Заменяет сырой Map<String, Object> для типобезопасности.
 */
public record TelegramLinkResponse(
        String deepLink,
        String botUsername,
        LocalDateTime expiresAt) {
}

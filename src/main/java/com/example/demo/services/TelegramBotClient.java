package com.example.demo.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class TelegramBotClient {

    private final RestTemplate restTemplate;

    @Value("${telegram.bot.enabled:false}")
    private boolean botEnabled;

    // FIX 1: trim() при чтении — убирает случайные пробелы из properties
    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.api-base:https://api.telegram.org}")
    private String apiBase;

    public TelegramBotClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Регистрирует webhook URL в Telegram.
     * Вызывается один раз при старте приложения.
     */
    public void setWebhook(String webhookUrl) {
        if (!isConfigured()) {
            log.warn("setWebhook skipped — bot not configured");
            return;
        }
        try {
            String url = String.format("%s/bot%s/setWebhook", apiBase, botToken.trim());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("url", webhookUrl);

            var resp = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
            log.info("setWebhook → {} status={}", webhookUrl, resp.getStatusCode());
            log.info("setWebhook response: {}", resp.getBody());
        } catch (Exception ex) {
            log.error("setWebhook FAILED: {}", ex.getMessage());
        }
    }
    public boolean isConfigured() {
        // FIX 1: trim() — пробел в начале токена делал isBlank()=false, но Telegram отклонял запрос
        return botEnabled && botToken != null && !botToken.trim().isBlank();
    }

    /**
     * Отправляет сообщение пользователю в Telegram.
     *
     * @param chatId числовой ID чата Telegram
     * @param text   текст в формате MarkdownV2
     * @return true если успешно
     */
    public boolean sendMessage(String chatId, String text) {
        if (!isConfigured()) {
            log.debug("Telegram bot not configured — sendMessage skipped");
            return false;
        }
        if (chatId == null || chatId.isBlank() || text == null || text.isBlank()) {
            log.warn("sendMessage called with blank chatId or text");
            return false;
        }

        try {
            // FIX 1: trim() токена на случай пробелов из окружения
            String url = String.format("%s/bot%s/sendMessage", apiBase, botToken.trim());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // HashMap вместо Map.of() — позволяет явно НЕ добавлять parse_mode.
            // parse_mode НЕ используется: любой спецсимвол в названии задачи
            // (точки, скобки, дефисы и т.д.) вызывал 400 Bad Request при MarkdownV2.
            // Plain text всегда доходит без ошибок.
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            payload.put("disable_web_page_preview", true);

            var resp = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
            log.info("Telegram sendMessage OK → chatId=***{} status={}",
                    safeSuffix(chatId), resp.getStatusCode());
            return true;

        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            log.error("Telegram sendMessage FAILED → chatId=***{} httpStatus={} body={}",
                    safeSuffix(chatId), ex.getStatusCode(), ex.getResponseBodyAsString());
            return false;
        } catch (Exception ex) {
            log.error("Telegram sendMessage ERROR → chatId=***{} error={}",
                    safeSuffix(chatId), ex.getMessage());
            return false;
        }
    }

    private String safeSuffix(String value) {
        if (value == null || value.length() < 4)
            return "????";
        return value.substring(value.length() - 4);
    }
}

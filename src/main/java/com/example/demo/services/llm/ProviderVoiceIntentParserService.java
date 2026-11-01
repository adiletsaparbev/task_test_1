package com.example.demo.services.llm;

import com.example.demo.config.VoiceParseProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderVoiceIntentParserService implements VoiceIntentParserService {

    private final VoiceParseProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public VoiceParseResult parse(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return null;
        }

        String provider = properties.getProvider() == null ? "rule" : properties.getProvider().trim().toLowerCase();

        return switch (provider) {
            case "openai" -> parseWithOpenAi(transcript);
            case "gemini" -> {
                try {
                    yield parseWithGemini(transcript);
                } catch (IllegalStateException ex) {
                    if (isQuotaExceeded(ex) && isProviderConfigured(properties.getOpenai())) {
                        log.warn("[LLM] Gemini quota exceeded, fallback to OpenAI parse");
                        yield parseWithOpenAi(transcript);
                    }
                    throw ex;
                }
            }
            case "rule", "none", "disabled", "off" -> null;
            default -> throw new IllegalArgumentException("Неподдерживаемый LLM parser provider: " + provider);
        };
    }

    private VoiceParseResult parseWithOpenAi(String transcript) {
        VoiceParseProperties.Provider p = properties.getOpenai();
        validateProvider(p, "openai");

        try {
            String systemPrompt = """
                    Ты парсер голосовых команд для task-management.
                    Извлеки поля задачи из свободной речи на русском.
                    Верни ТОЛЬКО JSON без markdown:
                    {
                      "assigneeRaw": string|null,
                      "title": string|null,
                      "description": string|null,
                      "dueDateRaw": string|null,
                      "priorityRaw": string|null,
                      "confidence": number
                    }
                    Требования:
                    - не выдумывай факты
                    - если поле не найдено, ставь null
                    - confidenc e от 0 до 1
                    - dueDateRaw верни в формате yyyy-MM-dd (например "2026-04-28")
                    - если точную дату вывести нельзя, верни null
                    """;

            JsonNode payload = objectMapper.readTree("""
                    {
                      "model": "%s",
                      "temperature": 0,
                      "messages": [
                        {"role": "system", "content": %s},
                        {"role": "user", "content": %s}
                      ]
                    }
                    """.formatted(
                    escapeJson(p.getModel()),
                    quoteJson(systemPrompt),
                    quoteJson(transcript)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(p.getBaseUrl()) + "/chat/completions"))
                    .header("Authorization", "Bearer " + p.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload),
                            StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response, "OpenAI parse");

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            return parseResultJson(content);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ошибка LLM parser (openai): " + e.getMessage(), e);
        }
    }

    private static final int GEMINI_MAX_RETRIES = 2;
    private static final long GEMINI_MAX_WAIT_MS = 45_000L;

    private VoiceParseResult parseWithGemini(String transcript) {
        VoiceParseProperties.Provider p = properties.getGemini();
        validateProvider(p, "gemini");

        try {
            String prompt = """
                    Ты парсер голосовых команд для task-management.
                    Извлеки поля задачи из свободной речи на русском.
                    Верни ТОЛЬКО JSON без markdown в формате:
                    {
                      "assigneeRaw": string|null,
                      "title": string|null,
                      "description": string|null,
                      "dueDateRaw": string|null,
                      "priorityRaw": string|null,
                      "confidence": number
                    }
                    Правила:
                    - Не выдумывай данные
                    - Не добавляй комментарии
                    - Если поле не найдено, ставь null
                    - dueDateRaw возвращай в формате yyyy-MM-dd; если не уверен, верни null
                    - title: короткое название задачи (3-12 слов), не весь транскрипт
                    - description: только описание, без дедлайна/приоритета/исполнителя

                    Текст команды: """
                    + transcript;

            // Безопасное построение JSON через Jackson ObjectNode (Jackson сам экранирует)
            com.fasterxml.jackson.databind.node.ObjectNode partNode = objectMapper.createObjectNode();
            partNode.put("text", prompt);
            com.fasterxml.jackson.databind.node.ArrayNode partsArray = objectMapper.createArrayNode();
            partsArray.add(partNode);
            com.fasterxml.jackson.databind.node.ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.set("parts", partsArray);
            com.fasterxml.jackson.databind.node.ArrayNode contentsArray = objectMapper.createArrayNode();
            contentsArray.add(contentNode);
            com.fasterxml.jackson.databind.node.ObjectNode genConfig = objectMapper.createObjectNode();
            genConfig.put("temperature", 0);
            genConfig.put("responseMimeType", "application/json");
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.set("contents", contentsArray);
            payload.set("generationConfig", genConfig);

            String model = resolveGeminiModel(p.getModel());
            String url = buildGeminiGenerateContentUrl(p, model);
            String payloadStr = objectMapper.writeValueAsString(payload);

            HttpResponse<String> response = null;
            for (int attempt = 1; attempt <= GEMINI_MAX_RETRIES; attempt++) {
                log.info("[Gemini parse] attempt {}/{}, model: {}", attempt, GEMINI_MAX_RETRIES, model);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payloadStr, StandardCharsets.UTF_8))
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                log.info("[Gemini parse] response status: {}, body prefix: {}",
                        response.statusCode(),
                        response.body().length() > 400 ? response.body().substring(0, 400) : response.body());

                if (response.statusCode() == 429 && attempt < GEMINI_MAX_RETRIES) {
                    long waitMs = extractRetryAfterMs(response.body());
                    waitMs = Math.min(waitMs, GEMINI_MAX_WAIT_MS);
                    log.warn("[Gemini parse] 429 quota exceeded, waiting {}ms before retry (attempt {}/{})",
                            waitMs, attempt, GEMINI_MAX_RETRIES);
                    Thread.sleep(waitMs);
                    continue;
                }
                break;
            }

            ensureSuccess(response, "Gemini parse");

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text")
                    .asText("").trim();
            return parseResultJson(content);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Gemini parse] IO/Interrupted error: {}", e.getMessage(), e);
            throw new IllegalStateException("Ошибка LLM parser (gemini): " + e.getMessage(), e);
        }
    }

    private VoiceParseResult parseResultJson(String content) throws IOException {
        if (content == null || content.isBlank()) {
            return null;
        }

        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```json", "")
                    .replaceAll("^```", "")
                    .replaceAll("```$", "")
                    .trim();
        }

        JsonNode node = objectMapper.readTree(cleaned);
        return new VoiceParseResult(
                nullableText(node, "assigneeRaw"),
                nullableText(node, "title"),
                nullableText(node, "description"),
                nullableText(node, "dueDateRaw"),
                nullableText(node, "priorityRaw"),
                node.path("confidence").isNumber() ? node.path("confidence").asDouble() : null);
    }

    private String nullableText(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? null : value;
    }

    private void validateProvider(VoiceParseProperties.Provider provider, String providerName) {
        if (provider == null || isBlank(provider.getBaseUrl()) || isBlank(provider.getApiKey())
                || isBlank(provider.getModel())) {
            throw new IllegalStateException(
                    "LLM parser provider " + providerName + " не настроен. Проверьте application.properties/env");
        }
    }

    private void ensureSuccess(HttpResponse<String> response, String providerName) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }

        if (providerName != null && providerName.toLowerCase().contains("gemini") && response.statusCode() == 403) {
            String body = response.body() == null ? "" : response.body();
            try {
                JsonNode root = objectMapper.readTree(body);
                String message = root.path("error").path("message").asText("").trim();
                String reason = root.path("error").path("details").path(0).path("reason").asText("").trim();
                String activationUrl = root.path("error").path("details").path(0).path("metadata")
                        .path("activationUrl").asText("").trim();

                if ("SERVICE_DISABLED".equalsIgnoreCase(reason)) {
                    throw new IllegalStateException(
                            "Gemini API отключён для проекта ключа. Включите Generative Language API: "
                                    + (activationUrl.isBlank()
                                            ? "https://console.developers.google.com/apis/api/generativelanguage.googleapis.com/overview"
                                            : activationUrl));
                }

                if (!message.isBlank()) {
                    throw new IllegalStateException("Gemini доступ запрещён (403): " + message);
                }
            } catch (IOException ignored) {
                // fallback to default message below
            }
        }

        if (providerName != null && providerName.toLowerCase().contains("gemini") && response.statusCode() == 429) {
            String body = response.body() == null ? "" : response.body();
            try {
                JsonNode root = objectMapper.readTree(body);
                String message = root.path("error").path("message").asText("").trim();
                throw new IllegalStateException(
                        "Gemini quota exceeded (429). Проверьте billing/limits: https://ai.google.dev/gemini-api/docs/rate-limits"
                                + (message.isBlank() ? "" : " | " + message));
            } catch (IOException ignored) {
                throw new IllegalStateException(
                        "Gemini quota exceeded (429). Проверьте billing/limits: https://ai.google.dev/gemini-api/docs/rate-limits");
            }
        }

        throw new IllegalStateException(
                providerName + " вернул ошибку " + response.statusCode() + ": " + response.body());
    }

    /** Извлекает задержку retry из тела 429-ответа Gemini ("retry in X.Xs") или возвращает 35 секунд по умолчанию */
    private long extractRetryAfterMs(String body) {
        if (body == null || body.isBlank()) return 35_000L;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("retry in ([0-9]+(?:\\.[0-9]+)?)s", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (m.find()) {
            try {
                double seconds = Double.parseDouble(m.group(1));
                return (long) (seconds * 1000) + 1000L; // +1s запас
            } catch (NumberFormatException ignored) {
            }
        }
        return 35_000L;
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String buildGeminiGenerateContentUrl(VoiceParseProperties.Provider provider, String model) {
        String baseUrl = resolveGeminiBaseUrl(provider.getBaseUrl());
        return trimTrailingSlash(baseUrl) + "/models/" + model + ":generateContent?key=" + provider.getApiKey();
    }

    private String resolveGeminiBaseUrl(String configuredBaseUrl) {
        String base = trimTrailingSlash(configuredBaseUrl);
        if (isBlank(base)) {
            return "https://generativelanguage.googleapis.com/v1beta";
        }

        // Совместимость: пользователь мог поставить неподдерживаемый endpoint
        if (base.contains("api.gemini.com/v1/ai")) {
            log.warn("[Gemini] base-url '{}' не поддерживает generateContent API. Используется fallback base-url.",
                    base);
            return "https://generativelanguage.googleapis.com/v1beta";
        }

        return base;
    }

    private String resolveGeminiModel(String configuredModel) {
        if (isBlank(configuredModel)) {
            return "gemini-2.0-flash";
        }

        String model = configuredModel.trim();
        if (model.startsWith("http://") || model.startsWith("https://")) {
            log.warn("[Gemini] model задан как URL ('{}'). Используется fallback model gemini-2.0-flash.", model);
            return "gemini-2.0-flash";
        }

        return model;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isProviderConfigured(VoiceParseProperties.Provider provider) {
        return provider != null
                && !isBlank(provider.getBaseUrl())
                && !isBlank(provider.getApiKey())
                && !isBlank(provider.getModel());
    }

    private boolean isQuotaExceeded(Throwable ex) {
        if (ex == null || ex.getMessage() == null) {
            return false;
        }
        String msg = ex.getMessage().toLowerCase();
        return msg.contains("429")
                || msg.contains("quota")
                || msg.contains("resource_exhausted");
    }

    private String quoteJson(String text) {
        return "\"" + escapeJson(text) + "\"";
    }

    private String escapeJson(String text) {
        if (text == null)
            return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

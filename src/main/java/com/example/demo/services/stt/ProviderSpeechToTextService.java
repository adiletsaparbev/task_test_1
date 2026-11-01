package com.example.demo.services.stt;

import com.example.demo.config.VoiceSttProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderSpeechToTextService implements SpeechToTextService {

    private final VoiceSttProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String transcribe(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            return null;
        }

        String provider = properties.getProvider() == null ? "client" : properties.getProvider().trim().toLowerCase();
        return switch (provider) {
            case "openai" -> transcribeWithOpenAi(audio);
            case "gemini" -> transcribeWithGemini(audio);
            case "client", "browser", "none", "disabled" -> null;
            default -> throw new IllegalArgumentException("Неподдерживаемый STT provider: " + provider);
        };
    }

    private String transcribeWithOpenAi(MultipartFile audio) {
        VoiceSttProperties.Provider provider = properties.getOpenai();
        validateProvider(provider, "openai");

        String boundary = "----VoiceTaskBoundary" + UUID.randomUUID();
        String mimeType = resolveMimeType(audio);
        String fileName = audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "voice.webm";

        try {
            byte[] fileBytes = audio.getBytes();
            byte[] body = buildOpenAiMultipartBody(boundary, provider.getModel(), fileName, mimeType, fileBytes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(provider.getBaseUrl()) + "/audio/transcriptions"))
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response, "OpenAI STT");

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("text").asText("").trim();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ошибка вызова OpenAI STT: " + e.getMessage(), e);
        }
    }

    private String transcribeWithGemini(MultipartFile audio) {
        VoiceSttProperties.Provider provider = properties.getGemini();
        validateProvider(provider, "gemini");

        try {
            String base64Audio = Base64.getEncoder().encodeToString(audio.getBytes());
            String mimeType = resolveMimeType(audio);

            JsonNode payload = objectMapper.readTree(
                    """
                            {
                              \"contents\": [
                                {
                                  \"parts\": [
                                    {
                                      \"text\": \"Сделай точную транскрипцию аудио на русском языке. Верни только текст речи без пояснений, markdown и комментариев.\"
                                    },
                                    {
                                      \"inline_data\": {
                                        \"mime_type\": \"%s\",
                                        \"data\": \"%s\"
                                      }
                                    }
                                  ]
                                }
                              ],
                              \"generationConfig\": {
                                \"temperature\": 0
                              }
                            }
                            """
                            .formatted(mimeType, base64Audio));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(provider.getBaseUrl()) + "/models/" + provider.getModel()
                            + ":generateContent?key=" + provider.getApiKey()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload),
                            StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response, "Gemini STT");

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            return textNode.asText("").trim();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ошибка вызова Gemini STT: " + e.getMessage(), e);
        }
    }

    private void validateProvider(VoiceSttProperties.Provider provider, String providerName) {
        if (provider == null || isBlank(provider.getBaseUrl()) || isBlank(provider.getApiKey())
                || isBlank(provider.getModel())) {
            throw new IllegalStateException(
                    "STT provider " + providerName + " не настроен. Проверьте application.properties/env");
        }
    }

    private byte[] buildOpenAiMultipartBody(String boundary,
            String model,
            String fileName,
            String mimeType,
            byte[] fileBytes) throws IOException {
        String lineBreak = "\r\n";
        byte[] prefix = ("--" + boundary + lineBreak +
                "Content-Disposition: form-data; name=\"model\"" + lineBreak + lineBreak +
                model + lineBreak +
                "--" + boundary + lineBreak +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + lineBreak +
                "Content-Type: " + mimeType + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = (lineBreak + "--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[prefix.length + fileBytes.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(fileBytes, 0, body, prefix.length, fileBytes.length);
        System.arraycopy(suffix, 0, body, prefix.length + fileBytes.length, suffix.length);
        return body;
    }

    private void ensureSuccess(HttpResponse<String> response, String providerName) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw new IllegalStateException(
                providerName + " вернул ошибку " + response.statusCode() + ": " + response.body());
    }

    private String resolveMimeType(MultipartFile audio) {
        if (audio.getContentType() != null && !audio.getContentType().isBlank()) {
            return audio.getContentType();
        }
        return "audio/webm";
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

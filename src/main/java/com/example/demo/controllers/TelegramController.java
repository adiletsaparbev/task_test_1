package com.example.demo.controllers;

import com.example.demo.dto.response.TelegramLinkResponse;
import com.example.demo.entity.User;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.TelegramBotClient;
import com.example.demo.services.TelegramLinkService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/telegram")
@SecurityRequirement(name = "bearerAuth")
public class TelegramController {

    private final UserRepository userRepository;
    private final TelegramLinkService telegramLinkService;
    private final TelegramBotClient telegramBotClient;

    @Value("${telegram.webhook.secret-token:}")
    private String webhookSecretToken;

    /** Статус привязки Telegram для текущего пользователя */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        return ResponseEntity.ok(Map.of(
                "linked", user.getTelegramChatId() != null && !user.getTelegramChatId().isBlank(),
                "enabled", user.isTelegramNotificationsEnabled()));
    }

    /** Генерация одноразовой ссылки для привязки Telegram */
    @PostMapping("/link/request")
    public ResponseEntity<TelegramLinkResponse> requestLink(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(telegramLinkService.createLink(getUser(userDetails)));
    }

    /** Включить / выключить уведомления */
    @PatchMapping("/enabled")
    public ResponseEntity<Void> setEnabled(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam boolean enabled) {
        telegramLinkService.setEnabled(getUser(userDetails), enabled);
        return ResponseEntity.ok().build();
    }

    /** Отвязать Telegram аккаунт */
    @DeleteMapping("/link")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal UserDetails userDetails) {
        telegramLinkService.unlink(getUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    /**
     * [ADMIN] Генерация ссылки для любого пользователя по email.
     * Используется для отладки / помощи пользователям.
     */
    @PostMapping("/admin/link-for")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TelegramLinkResponse> adminLinkFor(@RequestParam String email) {
        User target = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + email));
        return ResponseEntity.ok(telegramLinkService.createLink(target));
    }

    /**
     * Webhook endpoint для входящих обновлений от Telegram.
     * Публичный — исключён из фильтра JWT в SecurityConfig.
     * ЗАЩИЩЁН: проверяет X-Telegram-Bot-Api-Secret-Token для защиты от
     * несанкционированных запросов.
     * ВСЕГДА возвращает HTTP 200, чтобы Telegram не повторял запросы.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String receivedToken,
            @RequestBody Map<String, Object> update,
            HttpServletRequest request) {

        // Проверка секретного токена (защита от поддельных webhook запросов)
        if (webhookSecretToken != null && !webhookSecretToken.isEmpty()) {
            if (receivedToken == null || !receivedToken.equals(webhookSecretToken)) {
                log.warn("[SECURITY] Получен webhook без валидного секретного токена от IP: {}",
                        request.getRemoteAddr());
                // Возвращаем 200 OK, чтобы не выдавать информацию атакующему
                // но не обрабатываем запрос
                return ResponseEntity.ok(Map.of("ok", false, "error", "Unauthorized"));
            }
        } else {
            log.warn("[SECURITY] Telegram webhook работает БЕЗ проверки secret token! " +
                    "Установите telegram.webhook.secret-token в application.properties");
        }

        try {
            processUpdate(update);
        } catch (Exception e) {
            log.error("Ошибка обработки Telegram webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── private helpers ───────────────────────────────────────────────────────

    private void processUpdate(Map<String, Object> update) {
        Object msgObj = update.get("message");
        if (!(msgObj instanceof Map<?, ?> msg))
            return;

        Object textObj = msg.get("text");
        if (!(textObj instanceof String text) || !text.startsWith("/start"))
            return;

        String[] parts = text.trim().split("\\s+", 2);
        if (parts.length < 2) {
            log.debug("Telegram /start без токена");
            return;
        }

        Object chatObj = msg.get("chat");
        if (!(chatObj instanceof Map<?, ?> chatMap))
            return;

        Object chatIdObj = chatMap.get("id");
        if (chatIdObj == null)
            return;

        String token = parts[1];
        String chatId = String.valueOf(chatIdObj);

        boolean linked = telegramLinkService.consumeStartToken(token, chatId);
        if (linked) {
            telegramBotClient.sendMessage(chatId,
                    "✅ Telegram уведомления успешно подключены. Теперь вы будете получать уведомления о задачах здесь.");
        } else {
            telegramBotClient.sendMessage(chatId,
                    "⚠️ Ссылка недействительна или уже использована. Запросите новую в настройках приложения.");
        }
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
    }
}

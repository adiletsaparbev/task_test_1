package com.example.demo.services;

import com.example.demo.dto.response.TelegramLinkResponse;
import com.example.demo.entity.TelegramLinkToken;
import com.example.demo.entity.User;
import com.example.demo.repositories.TelegramLinkTokenRepository;
import com.example.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    private final TelegramLinkTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Value("${telegram.bot.username:}")
    private String botUsername;

    @Value("${telegram.link.ttl-minutes:20}")
    private long linkTtlMinutes;

    /**
     * Создаёт новый одноразовый deep-link для привязки Telegram аккаунта.
     * Предыдущий токен пользователя и просроченные токены удаляются.
     */
    @Transactional
    public TelegramLinkResponse createLink(User user) {
        if (botUsername == null || botUsername.isBlank()) {
            throw new IllegalStateException("Telegram bot username не настроен (telegram.bot.username)");
        }

        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.deleteExpiredTokens(LocalDateTime.now());

        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(linkTtlMinutes);

        tokenRepository.save(TelegramLinkToken.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .build());

        log.info("Telegram link created for userId={} expiresAt={}", user.getId(), expiresAt);

        return new TelegramLinkResponse(
                "https://t.me/" + botUsername + "?start=" + token,
                botUsername,
                expiresAt);
    }

    /**
     * Потребляет start-токен из Telegram вебхука, привязывая chatId к пользователю.
     *
     * @return true если токен валиден и привязка выполнена
     */
    @Transactional
    public boolean consumeStartToken(String token, String chatId) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            return false;
        }

        TelegramLinkToken link = tokenRepository.findByToken(token).orElse(null);
        if (link == null) {
            log.debug("consumeStartToken: token not found");
            return false;
        }
        if (link.isUsed()) {
            log.debug("consumeStartToken: token already used for userId={}", link.getUser().getId());
            return false;
        }
        if (link.isExpired()) {
            log.debug("consumeStartToken: token expired for userId={}", link.getUser().getId());
            return false;
        }

        User user = link.getUser();
        user.setTelegramChatId(chatId);
        user.setTelegramNotificationsEnabled(true);
        userRepository.save(user);

        link.setUsedAt(LocalDateTime.now());
        tokenRepository.save(link);

        log.info("Telegram linked: userId={} chatId=***{}", user.getId(),
                chatId.length() >= 4 ? chatId.substring(chatId.length() - 4) : "??");
        return true;
    }

    @Transactional
    public void unlink(User user) {
        user.setTelegramChatId(null);
        user.setTelegramNotificationsEnabled(false);
        userRepository.save(user);
        tokenRepository.deleteByUserId(user.getId());
        log.info("Telegram unlinked for userId={}", user.getId());
    }

    @Transactional
    public void setEnabled(User user, boolean enabled) {
        user.setTelegramNotificationsEnabled(enabled);
        userRepository.save(user);
        log.info("Telegram notifications {} for userId={}", enabled ? "enabled" : "disabled", user.getId());
    }

    private String generateToken() {
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}

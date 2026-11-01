package com.example.demo.config;

import com.example.demo.services.TelegramBotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramWebhookInitializer implements ApplicationRunner {

    private final TelegramBotClient telegramBotClient;

    @Value("${telegram.webhook.url:}")
    private String webhookUrl;

    @Override
    public void run(ApplicationArguments args) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("telegram.webhook.url не задан — webhook не зарегистрирован автоматически");
            return;
        }
        log.info("Регистрация Telegram webhook: {}", webhookUrl);
        telegramBotClient.setWebhook(webhookUrl + "/api/telegram/webhook");
    }
}
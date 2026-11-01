package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<String> statements = List.of(
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS telegram_chat_id VARCHAR(64)",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS telegram_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE",
                "CREATE TABLE IF NOT EXISTS telegram_link_tokens (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "user_id BIGINT NOT NULL, " +
                        "token VARCHAR(120) NOT NULL UNIQUE, " +
                        "expires_at TIMESTAMP NOT NULL, " +
                        "used_at TIMESTAMP NULL, " +
                        "created_at TIMESTAMP NULL, " +
                        "CONSTRAINT fk_telegram_link_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                        +
                        ")");

        for (String sql : statements) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception ex) {
                log.warn("Schema migration statement failed: {} | reason={}", sql, ex.getMessage());
            }
        }

        log.info("Telegram schema migration check completed");
    }
}

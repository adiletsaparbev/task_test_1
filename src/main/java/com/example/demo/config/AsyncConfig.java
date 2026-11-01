package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * FIX 3: Без @EnableAsync аннотация @Async на TelegramNotificationService.send()
 * игнорируется Spring-ом — метод выполняется синхронно, а исключения глотаются молча.
 *
 * AsyncUncaughtExceptionHandler гарантирует, что ошибки в @Async методах
 * попадают в лог, а не исчезают бесследно.
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("telegram-async-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("[ASYNC ERROR] Метод: {}.{}() — {}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        ex.getMessage(), ex);
    }
}
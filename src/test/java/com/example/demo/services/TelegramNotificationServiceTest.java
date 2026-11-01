package com.example.demo.services;

import com.example.demo.entity.Task;
import com.example.demo.entity.User;
import com.example.demo.enums.Priority;
import com.example.demo.enums.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TelegramNotificationService.
 * Tests notification sending logic with mocked Telegram API client.
 */
@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock
    private TelegramBotClient telegramBotClient;

    @InjectMocks
    private TelegramNotificationService telegramNotificationService;

    private User testUser;
    private Task testTask;

    @BeforeEach
    void setUp() {
        when(telegramBotClient.isConfigured()).thenReturn(true);

        testUser = User.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .telegramChatId("123456789")
                .telegramNotificationsEnabled(true)
                .build();

        testTask = Task.builder()
                .id(100L)
                .title("Test Task")
                .description("Test Description")
                .priority(Priority.HIGH)
                .status(TaskStatus.NEW)
                .build();
    }

    @Test
    void shouldSendTelegramNotification_whenTaskAssigned() {
        // When
        when(telegramBotClient.sendMessage(anyString(), anyString())).thenReturn(true);
        telegramNotificationService.send(testUser, "TASK_ASSIGNED", "Вам назначена новая задача", testTask);

        // Then
        verify(telegramBotClient, times(1))
                .sendMessage(eq("123456789"), contains("Test Task"));
    }

    @Test
    void shouldNotSendNotification_whenUserHasNoTelegramLinked() {
        // Given
        testUser.setTelegramChatId(null);

        // When
        telegramNotificationService.send(testUser, "TASK_ASSIGNED", "Test message", testTask);

        // Then
        verify(telegramBotClient, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void shouldNotSendNotification_whenNotificationsDisabled() {
        // Given
        testUser.setTelegramNotificationsEnabled(false);

        // When
        telegramNotificationService.send(testUser, "TASK_ASSIGNED", "Test message", testTask);

        // Then
        verify(telegramBotClient, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void shouldHandleException_whenTelegramApiThrowsError() {
        // Given
        when(telegramBotClient.sendMessage(anyString(), anyString())).thenReturn(false);

        // When - should not propagate exception
        telegramNotificationService.send(testUser, "TASK_ASSIGNED", "Test message", testTask);

        // Then - method should complete without error (logged as warning)
        verify(telegramBotClient, times(1)).sendMessage(anyString(), anyString());
    }

    @Test
    void shouldNotSendNotification_whenBotNotConfigured() {
        // Given
        when(telegramBotClient.isConfigured()).thenReturn(false);

        // When
        telegramNotificationService.send(testUser, "TASK_ASSIGNED", "Test message", testTask);

        // Then
        verify(telegramBotClient, never()).sendMessage(anyString(), anyString());
    }
}

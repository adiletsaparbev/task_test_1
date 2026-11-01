package com.example.demo.controllers;

import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.entity.User;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.NotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getAll(getUserId(userDetails)));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnread(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getUnread(getUserId(userDetails)));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countUnread(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                Map.of("count", notificationService.countUnread(getUserId(userDetails))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.markAllAsRead(getUserId(userDetails));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.delete(id, getUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAllForUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.deleteAllForUser(userId, getUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(UserDetails userDetails) {
        return getUser(userDetails).getId();
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
    }
}
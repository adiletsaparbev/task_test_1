package com.example.demo.controllers;

import com.example.demo.dto.request.CommentRequest;
import com.example.demo.dto.request.CommentUpdateRequest;
import com.example.demo.dto.response.CommentResponse;
import com.example.demo.services.CommentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/api/tasks/{taskId}/comments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<CommentResponse>> getAll(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "asc") String sort,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(commentService.getCommentsByTask(taskId, sort, userDetails.getUsername()));
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<CommentResponse> add(
            @PathVariable Long taskId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(taskId, request, userDetails.getUsername()));
    }

    @PatchMapping("/api/comments/{commentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CommentResponse> update(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                commentService.updateComment(commentId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/api/comments/{commentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        commentService.deleteComment(commentId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
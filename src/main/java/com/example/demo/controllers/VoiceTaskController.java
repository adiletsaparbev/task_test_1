package com.example.demo.controllers;

import com.example.demo.dto.request.VoiceTaskConfirmRequest;
import com.example.demo.dto.response.TaskResponse;
import com.example.demo.dto.response.VoiceTaskDraftResponse;
import com.example.demo.services.VoiceTaskService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks/voice")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VoiceTaskController {

    private final VoiceTaskService voiceTaskService;

    // audio -> transcript -> draft
    @PostMapping(value = "/parse", consumes = { "multipart/form-data" })
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<VoiceTaskDraftResponse> parse(
            @PathVariable Long projectId,
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestPart(value = "transcript", required = false) String transcript,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(voiceTaskService.parse(projectId, audio, transcript, userDetails.getUsername()));
    }

    // draft -> create task (reuse TaskService.createTask)
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM')")
    public ResponseEntity<TaskResponse> confirm(
            @PathVariable Long projectId,
            @Valid @RequestBody VoiceTaskConfirmRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(voiceTaskService.confirm(projectId, request, userDetails.getUsername()));
    }
}

package com.example.demo.controllers;

import com.example.demo.dto.request.TagCreateRequest;
import com.example.demo.dto.response.TagResponse;
import com.example.demo.services.TagService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    // Создать тег
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<TagResponse> create(
            @Valid @RequestBody TagCreateRequest request) {
        return ResponseEntity.ok(tagService.createTag(request));
    }

    // Получить все теги
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PM','TEAM')")
    public ResponseEntity<List<TagResponse>> getAll() {
        return ResponseEntity.ok(tagService.getAllTags());
    }

    // Удалить тег
    @DeleteMapping("/{tagId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long tagId) {
        tagService.deleteTag(tagId);
        return ResponseEntity.noContent().build();
    }
}
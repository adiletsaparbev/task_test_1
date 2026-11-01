package com.example.demo.services.llm;

public record VoiceParseResult(
        String assigneeRaw,
        String title,
        String description,
        String dueDateRaw,
        String priorityRaw,
        Double confidence) {
}

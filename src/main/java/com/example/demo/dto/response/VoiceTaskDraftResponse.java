package com.example.demo.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VoiceTaskDraftResponse {

    private String transcript;

    private String assigneeRaw;
    private Long assigneeId;

    private String title;
    private String description;
    private String dueDate;
    private String priority;

    private double confidence;

    private List<String> warnings = new ArrayList<>();
    private List<String> missingFields = new ArrayList<>();
    private List<VoiceAssigneeCandidateResponse> assigneeCandidates = new ArrayList<>();
}

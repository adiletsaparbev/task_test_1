package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoiceAssigneeCandidateResponse {
    private Long id;
    private String fullName;
    private double score;
}

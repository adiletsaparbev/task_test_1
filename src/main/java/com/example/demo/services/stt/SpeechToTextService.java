package com.example.demo.services.stt;

import org.springframework.web.multipart.MultipartFile;

public interface SpeechToTextService {
    String transcribe(MultipartFile audio);
}

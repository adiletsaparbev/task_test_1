package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "voice.stt")
public class VoiceSttProperties {

    private String provider = "client";
    private Provider openai = new Provider();
    private Provider gemini = new Provider();

    @Getter
    @Setter
    public static class Provider {
        private String baseUrl;
        private String apiKey;
        private String model;
    }
}

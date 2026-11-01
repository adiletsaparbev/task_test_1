package com.example.demo.services.llm;

public interface VoiceIntentParserService {
    VoiceParseResult parse(String transcript);
}

package com.example.kitchenbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.google.genai.Client;

@Configuration
public class AiConfig {

    @Value("${google.ai.gemini.api-key}")
    private String geminiKey;

    @Bean
    public Client googleGenAiClient() {
        return Client.builder()
                .apiKey(geminiKey)
                .build();
    }
}
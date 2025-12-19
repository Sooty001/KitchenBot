package com.example.kitchenbot.service;

import com.example.kitchenbot.util.SslUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SaluteSpeechService {

    @Value("${salute.auth}")
    private String authKey;
    private final RestTemplate restTemplate;

    public SaluteSpeechService() {
        this.restTemplate = new RestTemplate();
        SslUtil.disableSslVerification();
    }

    public String transcribe(File audioFile) {
        try {
            String token = getAccessToken();
            byte[] fileContent = Files.readAllBytes(audioFile.toPath());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.parseMediaType("audio/ogg;codecs=opus"));
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileContent, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://smartspeech.sber.ru/rest/v1/speech:recognize",
                    HttpMethod.POST, requestEntity, String.class
            );
            return extractTextFromJson(response.getBody());
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTextFromJson(String json) {
        if (json == null) return "";
        Pattern pattern = Pattern.compile("\"result\":\\[\"(.*?)\"\\]");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    public byte[] synthesize(String text) {
        try {
            String token = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Content-Type", "application/text");
            headers.set("Accept", "audio/ogg; codecs=opus");
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(text.getBytes(StandardCharsets.UTF_8), headers);

            String url = "https://smartspeech.sber.ru/rest/v1/text:synthesize?format=opus&voice=May_24000";
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, byte[].class);
            return response.getBody();
        } catch (Exception e) {
            return null;
        }
    }

    private String getAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(authKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("RqUID", UUID.randomUUID().toString());
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("scope", "SALUTE_SPEECH_PERS");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth", request, TokenResponse.class
        );
        if (response.getBody() != null) return response.getBody().access_token;
        throw new RuntimeException("Не удалось получить токен Salute");
    }

    private static class TokenResponse { public String access_token; }
}
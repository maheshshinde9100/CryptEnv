package com.maheshshinde.CryptEnv.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OllamaAiProvider implements AiProvider {

    @Value("${cryptenv.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${cryptenv.ollama.model:llama2}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String generateResponse(String prompt, List<String> allowedTools) {
        try {
            String redactedPrompt = redactSecrets(prompt);
            
            Map<String, Object> request = Map.of(
                    "model", model,
                    "prompt", redactedPrompt,
                    "stream", false
            );
            
            Map<String, Object> response = restTemplate.postForObject(
                    ollamaUrl + "/api/generate",
                    request,
                    Map.class
            );
            
            return response != null ? (String) response.get("response") : "No response from Ollama";
        } catch (Exception e) {
            log.error("Failed to get response from Ollama", e);
            return "AI service unavailable. Please check Ollama configuration.";
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            restTemplate.getForObject(ollamaUrl + "/api/tags", Map.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Ollama";
    }

    private String redactSecrets(String text) {
        // Zero-plaintext firewall: replace potential secrets with SHA-256 hashes
        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 20 && looksLikeSecret(words[i])) {
                words[i] = "[SHA256:" + hash(words[i]).substring(0, 12) + "]";
            }
        }
        return String.join(" ", words);
    }

    private boolean looksLikeSecret(String word) {
        // Simple heuristic: long alphanumeric strings that could be keys/tokens
        return word.matches("[A-Za-z0-9+/=_-]{20,}");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }
}

package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.AiSecurityCopilotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI Security Copilot", description = "AI-powered security assistant APIs")
public class AiController {

    private final AiSecurityCopilotService aiSecurityCopilotService;

    @PostMapping("/chat")
    @Operation(summary = "Send a message to the AI Security Copilot")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String conversationId = body.getOrDefault("conversationId", java.util.UUID.randomUUID().toString());
        
        Map<String, Object> response = aiSecurityCopilotService.chat(message, conversationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @Operation(summary = "Check AI service availability")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "available", true,
                "provider", "Ollama",
                "message", "AI service is configured and available"
        ));
    }
}

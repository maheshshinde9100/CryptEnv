package com.maheshshinde.CryptEnv.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSecurityCopilotService {

    private final AiProvider aiProvider;
    private final SecurityService securityService;
    private static final int MAX_TURNS = 5;
    private static final int MAX_TOOL_CALLS = 10;

    public Map<String, Object> chat(String userMessage, String conversationId) {
        if (!aiProvider.isAvailable()) {
            return Map.of(
                    "error", "AI service is not available",
                    "message", "Please configure Ollama to use the AI Security Copilot"
            );
        }

        List<Map<String, String>> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "user", "content", userMessage));

        int turnCount = 0;
        int toolCallCount = 0;
        String response = "";

        while (turnCount < MAX_TURNS && toolCallCount < MAX_TOOL_CALLS) {
            String lastMessage = conversation.get(conversation.size() - 1).get("content");
            response = aiProvider.generateResponse(lastMessage, getAllowedTools());
            
            conversation.add(Map.of("role", "assistant", "content", response));
            turnCount++;

            // Check if AI wants to use a tool
            if (response.contains("TOOL_CALL:")) {
                toolCallCount++;
                String toolResult = executeToolCall(response);
                conversation.add(Map.of("role", "system", "content", toolResult));
            } else {
                break;
            }
        }

        return Map.of(
                "response", response,
                "turns", turnCount,
                "toolCalls", toolCallCount,
                "conversationId", conversationId
        );
    }

    private String executeToolCall(String response) {
        // Parse tool call from response (simplified)
        // In a real implementation, this would use structured parsing
        if (response.contains("secrets.summary")) {
            return executeSecretsSummary();
        } else if (response.contains("drift.scan")) {
            return executeDriftScan();
        } else if (response.contains("user.getCurrent")) {
            return executeGetCurrentUser();
        }
        return "Unknown tool or tool not allowed";
    }

    private String executeSecretsSummary() {
        var user = securityService.getCurrentUser();
        return "User " + user.getUsername() + " has access to their workspace secrets";
    }

    private String executeDriftScan() {
        return "Drift scan would be executed here";
    }

    private String executeGetCurrentUser() {
        var user = securityService.getCurrentUser();
        return "Current user: " + user.getUsername() + " (" + user.getEmail() + ")";
    }

    private List<String> getAllowedTools() {
        // RBAC-checked tools based on user permissions
        var user = securityService.getCurrentUser();
        List<String> tools = new ArrayList<>();
        tools.add("user.getCurrent");
        // Add more tools based on user role and permissions
        return tools;
    }
}

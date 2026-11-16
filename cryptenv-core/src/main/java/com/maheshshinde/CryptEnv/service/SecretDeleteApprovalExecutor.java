package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.model.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Executes an approved {@code SECRET_DELETE} approval request (§10) by deleting the environment
 * secret identified in the request payload.
 * <p>
 * Runs in its own transaction ({@link Propagation#REQUIRES_NEW}) so a failure cannot roll back
 * the approval record itself.
 */
@Component
@RequiredArgsConstructor
public class SecretDeleteApprovalExecutor implements ApprovalActionExecutor {

    private final SecretService secretService;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return ApprovalService.ACTION_SECRET_DELETE;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String execute(ApprovalRequest request) {
        Map<String, Object> payload = readJson(request.getPayload());
        Long environmentId = longValue(payload.get("environmentId"));
        String key = payload.get("key") != null ? payload.get("key").toString() : request.getResourceId();
        if (environmentId == null || key == null || key.isBlank()) {
            throw new IllegalArgumentException("SECRET_DELETE requires payload {environmentId, key}");
        }
        secretService.deleteSecret(environmentId, key);
        return "Deleted secret '" + key + "' from environment " + environmentId;
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Approval payload is not a JSON object");
        }
    }

    private Long longValue(Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

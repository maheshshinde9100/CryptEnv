package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import com.maheshshinde.CryptEnv.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Outbound webhooks (§10): HMAC-SHA256-signed delivery, retries, delivery log and redelivery.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Outbound webhook endpoints and delivery log")
public class WebhookController {

    private final WebhookService webhookService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @GetMapping
    @Operation(summary = "List the caller's webhook endpoints")
    public ResponseEntity<List<Map<String, Object>>> endpoints() {
        return ResponseEntity.ok(webhookService.listEndpoints());
    }

    @PostMapping
    @Operation(summary = "Create a webhook endpoint (returns the signing secret once)")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        Map<String, Object> endpoint = webhookService.createEndpoint(
                stringValue(body, "url"),
                stringValue(body, "description"),
                longValue(body, "workspaceId"),
                toStringList(body.get("eventTypes")));
        auditLogService.logEvent(securityService.getCurrentUser(), "WEBHOOK_CREATE", "WEBHOOK",
                String.valueOf(endpoint.get("id")), true, request, "Created webhook " + body.get("url"));
        return ResponseEntity.ok(endpoint);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a webhook endpoint")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        Map<String, Object> endpoint = webhookService.updateEndpoint(
                id,
                stringValue(body, "url"),
                stringValue(body, "description"),
                body.containsKey("eventTypes") ? toStringList(body.get("eventTypes")) : null,
                booleanValue(body, "active"));
        auditLogService.logEvent(securityService.getCurrentUser(), "WEBHOOK_UPDATE", "WEBHOOK",
                id.toString(), true, request, "Updated webhook " + id);
        return ResponseEntity.ok(endpoint);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a webhook endpoint and its deliveries")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        webhookService.deleteEndpoint(id);
        auditLogService.logEvent(securityService.getCurrentUser(), "WEBHOOK_DELETE", "WEBHOOK",
                id.toString(), true, request, "Deleted webhook " + id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate")
    @Operation(summary = "Rotate the signing secret (returns the new secret once)")
    public ResponseEntity<Map<String, Object>> rotate(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> endpoint = webhookService.rotateSecret(id);
        auditLogService.logEvent(securityService.getCurrentUser(), "WEBHOOK_ROTATE", "WEBHOOK",
                id.toString(), true, request, "Rotated signing secret for webhook " + id);
        return ResponseEntity.ok(endpoint);
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Send a signed test payload to an endpoint")
    public ResponseEntity<Map<String, Object>> test(@PathVariable Long id) {
        return ResponseEntity.ok(webhookService.testEndpoint(id));
    }

    @GetMapping("/deliveries")
    @Operation(summary = "List webhook deliveries (optionally filtered by endpoint)")
    public ResponseEntity<List<Map<String, Object>>> deliveries(@RequestParam(required = false) Long endpointId) {
        return ResponseEntity.ok(webhookService.listDeliveries(endpointId));
    }

    @PostMapping("/deliveries/{id}/redeliver")
    @Operation(summary = "Re-queue a webhook delivery")
    public ResponseEntity<Map<String, Object>> redeliver(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> delivery = webhookService.redeliver(id);
        auditLogService.logEvent(securityService.getCurrentUser(), "WEBHOOK_REDELIVER", "WEBHOOK_DELIVERY",
                id.toString(), true, request, "Re-queued webhook delivery " + id);
        return ResponseEntity.ok(delivery);
    }

    private String stringValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null ? value.toString() : null;
    }

    private Long longValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null && !"".equals(value) ? Long.valueOf(value.toString()) : null;
    }

    private Boolean booleanValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof Boolean b ? b : null;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}

package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.provider.ProviderTarget;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.ProviderSyncService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider Sync (§8): connect cloud/CI platforms and push workspace secrets to them.
 */
@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@Tag(name = "Provider Sync", description = "Push secrets to GitHub, Vercel, Cloudflare, Kubernetes, …")
public class ProviderIntegrationController {

    private final ProviderSyncService providerSyncService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @GetMapping
    @Operation(summary = "List available providers and the caller's connected integrations")
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("providers", providerSyncService.availableProviders());
        body.put("integrations", providerSyncService.listIntegrations());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{provider}/targets")
    @Operation(summary = "List push targets (repos / projects / namespaces) for a provider")
    public ResponseEntity<List<Map<String, Object>>> targets(@PathVariable String provider) {
        List<Map<String, Object>> targets = providerSyncService.listTargets(provider).stream()
                .map(this::toTargetView)
                .toList();
        return ResponseEntity.ok(targets);
    }

    @PostMapping("/{provider}/connect")
    @Operation(summary = "Connect a provider with a token and optional config")
    public ResponseEntity<Map<String, Object>> connect(
            @PathVariable String provider,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String token = stringValue(body, "token");
        if (token == null) {
            token = stringValue(body, "accessToken");
        }
        String name = stringValue(body, "name");
        Map<String, String> config = toStringMap(body.get("config"));

        Map<String, Object> result = providerSyncService.connect(provider, token, name, config);
        auditLogService.logEvent(securityService.getCurrentUser(), "PROVIDER_CONNECT", "INTEGRATION",
                provider, true, request, "Connected provider " + provider);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{provider}/test")
    @Operation(summary = "Health-check a connected provider")
    public ResponseEntity<Map<String, Object>> test(@PathVariable String provider) {
        return ResponseEntity.ok(providerSyncService.testConnection(provider));
    }

    @DeleteMapping("/{provider}")
    @Operation(summary = "Disconnect a provider (also removes its queued jobs)")
    public ResponseEntity<Void> disconnect(@PathVariable String provider, HttpServletRequest request) {
        providerSyncService.disconnect(provider);
        auditLogService.logEvent(securityService.getCurrentUser(), "PROVIDER_DISCONNECT", "INTEGRATION",
                provider, true, request, "Disconnected provider " + provider);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{provider}/sync")
    @Operation(summary = "Queue a push of environment secrets to a provider target")
    public ResponseEntity<Map<String, Object>> sync(
            @PathVariable String provider,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long environmentId = longValue(body, "environmentId");
        if (environmentId == null) {
            throw new IllegalArgumentException("environmentId is required");
        }
        String target = stringValue(body, "target");
        List<String> secretKeys = toStringList(body.get("secretKeys"));

        Map<String, Object> job = providerSyncService.enqueueSync(provider, environmentId, target, secretKeys);
        auditLogService.logEvent(securityService.getCurrentUser(), "PROVIDER_SYNC", "INTEGRATION",
                provider, true, request, "Queued sync to " + provider + " target=" + target);
        return ResponseEntity.accepted().body(job);
    }

    @PostMapping("/{provider}/delete-secret")
    @Operation(summary = "Queue removal of a pushed secret from a provider target")
    public ResponseEntity<Map<String, Object>> deleteSecret(
            @PathVariable String provider,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String target = stringValue(body, "target");
        String secretKey = stringValue(body, "secretKey");
        Map<String, Object> job = providerSyncService.enqueueDelete(provider, target, secretKey);
        auditLogService.logEvent(securityService.getCurrentUser(), "PROVIDER_DELETE_SECRET", "INTEGRATION",
                provider, true, request, "Queued delete of " + secretKey + " from " + target);
        return ResponseEntity.accepted().body(job);
    }

    @GetMapping("/jobs")
    @Operation(summary = "List this caller's sync jobs")
    public ResponseEntity<List<Map<String, Object>>> jobs() {
        return ResponseEntity.ok(providerSyncService.listJobs());
    }

    @GetMapping("/queue")
    @Operation(summary = "Sync queue status, including dead-letter jobs")
    public ResponseEntity<Map<String, Object>> queue() {
        return ResponseEntity.ok(providerSyncService.queueStatus());
    }

    @PostMapping("/jobs/{id}/replay")
    @Operation(summary = "Replay a dead-letter sync job")
    public ResponseEntity<Map<String, Object>> replay(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> job = providerSyncService.replay(id);
        auditLogService.logEvent(securityService.getCurrentUser(), "PROVIDER_SYNC_REPLAY", "SYNC_JOB",
                id.toString(), true, request, "Replayed sync job " + id);
        return ResponseEntity.ok(job);
    }

    @GetMapping(value = "/export/{environmentId}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Export environment secrets as .env, JSON, or docker-compose text")
    public ResponseEntity<String> export(@PathVariable Long environmentId,
                                         @RequestParam(defaultValue = "env") String format) {
        return ResponseEntity.ok(providerSyncService.exportEnvironment(environmentId, format));
    }

    private Map<String, Object> toTargetView(ProviderTarget target) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", target.id());
        view.put("name", target.name());
        view.put("url", target.url());
        return view;
    }

    private String stringValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null ? value.toString() : null;
    }

    private Long longValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null ? Long.valueOf(value.toString()) : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        ((Map<Object, Object>) map).forEach((k, v) -> out.put(String.valueOf(k), v != null ? String.valueOf(v) : null));
        return out;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}

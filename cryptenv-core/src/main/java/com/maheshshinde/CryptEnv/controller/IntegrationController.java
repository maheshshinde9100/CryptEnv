package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.Integration;
import com.maheshshinde.CryptEnv.model.IntegrationSyncLog;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import com.maheshshinde.CryptEnv.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "Cloud platform integration APIs")
public class IntegrationController {

    private final IntegrationService integrationService;
    private final AuthenticationService authenticationService;

    @GetMapping("/types")
    @Operation(summary = "Get all available integration types")
    public ResponseEntity<List<Map<String, Object>>> getIntegrationTypes() {
        return ResponseEntity.ok(integrationService.getAllIntegrationTypes());
    }

    @GetMapping("/workspace/{workspaceId}")
    @Operation(summary = "Get all integrations for a workspace")
    public ResponseEntity<List<Integration>> getWorkspaceIntegrations(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(integrationService.getWorkspaceIntegrations(workspaceId));
    }

    @GetMapping("/workspace/{workspaceId}/category/{category}")
    @Operation(summary = "Get integrations by category for a workspace")
    public ResponseEntity<List<Integration>> getWorkspaceIntegrationsByCategory(
            @PathVariable Long workspaceId, @PathVariable String category) {
        return ResponseEntity.ok(integrationService.getWorkspaceIntegrationsByCategory(workspaceId, category));
    }

    @PostMapping("/connect")
    @Operation(summary = "Connect an integration")
    public ResponseEntity<Integration> connectIntegration(@RequestBody Map<String, Object> body) {
        Long workspaceId = Long.valueOf(body.get("workspaceId").toString());
        String integrationType = (String) body.get("integrationType");
        String token = (String) body.get("token");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) body.get("config");
        var currentUser = authenticationService.getCurrentUser();
        
        Integration integration = integrationService.connectIntegration(workspaceId, integrationType, token, config, currentUser.getId());
        return ResponseEntity.ok(integration);
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Disconnect an integration")
    public ResponseEntity<Map<String, String>> disconnectIntegration(@RequestBody Map<String, String> body) {
        Long workspaceId = Long.valueOf(body.get("workspaceId"));
        String integrationType = body.get("integrationType");
        
        integrationService.disconnectIntegration(workspaceId, integrationType);
        return ResponseEntity.ok(Map.of("message", "Integration disconnected"));
    }

    @PostMapping("/{integrationId}/sync")
    @Operation(summary = "Trigger sync for an integration")
    public ResponseEntity<IntegrationSyncLog> syncIntegration(@PathVariable Long integrationId,
                                                             @RequestBody Map<String, String> body) {
        String syncType = body.getOrDefault("syncType", "full");
        IntegrationSyncLog syncLog = integrationService.syncIntegration(integrationId, syncType);
        return ResponseEntity.ok(syncLog);
    }

    @GetMapping("/{integrationId}/sync-logs")
    @Operation(summary = "Get sync logs for an integration")
    public ResponseEntity<List<IntegrationSyncLog>> getIntegrationSyncLogs(@PathVariable Long integrationId) {
        return ResponseEntity.ok(integrationService.getIntegrationSyncLogs(integrationId));
    }
}

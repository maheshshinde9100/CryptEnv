package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.ScopedGrant;
import com.maheshshinde.CryptEnv.service.ScopedGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scoped-grants")
@RequiredArgsConstructor
@Tag(name = "Scoped Grants", description = "Project- and environment-scoped grant APIs")
public class ScopedGrantController {

    private final ScopedGrantService scopedGrantService;

    @PostMapping
    @Operation(summary = "Create a scoped grant")
    public ResponseEntity<ScopedGrant> createGrant(@RequestBody Map<String, Object> body) {
        Long workspaceMemberId = Long.valueOf(body.get("workspaceMemberId").toString());
        Long projectId = body.get("projectId") != null ? Long.valueOf(body.get("projectId").toString()) : null;
        Long environmentId = body.get("environmentId") != null ? Long.valueOf(body.get("environmentId").toString()) : null;
        String grantType = (String) body.get("grantType");
        String permissions = (String) body.get("permissions");
        Long grantedBy = Long.valueOf(body.get("grantedBy").toString());
        LocalDateTime expiresAt = body.get("expiresAt") != null ? LocalDateTime.parse(body.get("expiresAt").toString()) : null;
        
        ScopedGrant grant = scopedGrantService.createGrant(workspaceMemberId, projectId, environmentId,
                grantType, permissions, grantedBy, expiresAt);
        return ResponseEntity.ok(grant);
    }

    @GetMapping("/member/{workspaceMemberId}")
    @Operation(summary = "Get all grants for a workspace member")
    public ResponseEntity<List<ScopedGrant>> getMemberGrants(@PathVariable Long workspaceMemberId) {
        return ResponseEntity.ok(scopedGrantService.getMemberGrants(workspaceMemberId));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get all grants for a project")
    public ResponseEntity<List<ScopedGrant>> getProjectGrants(@PathVariable Long projectId) {
        return ResponseEntity.ok(scopedGrantService.getProjectGrants(projectId));
    }

    @GetMapping("/environment/{environmentId}")
    @Operation(summary = "Get all grants for an environment")
    public ResponseEntity<List<ScopedGrant>> getEnvironmentGrants(@PathVariable Long environmentId) {
        return ResponseEntity.ok(scopedGrantService.getEnvironmentGrants(environmentId));
    }

    @DeleteMapping("/{grantId}")
    @Operation(summary = "Revoke a grant")
    public ResponseEntity<Map<String, String>> revokeGrant(@PathVariable Long grantId) {
        scopedGrantService.revokeGrant(grantId);
        return ResponseEntity.ok(Map.of("message", "Grant revoked"));
    }

    @PostMapping("/check-permission")
    @Operation(summary = "Check if a member has a specific permission")
    public ResponseEntity<Map<String, Boolean>> checkPermission(@RequestBody Map<String, Object> body) {
        Long workspaceMemberId = Long.valueOf(body.get("workspaceMemberId").toString());
        Long projectId = body.get("projectId") != null ? Long.valueOf(body.get("projectId").toString()) : null;
        Long environmentId = body.get("environmentId") != null ? Long.valueOf(body.get("environmentId").toString()) : null;
        String permission = (String) body.get("permission");
        
        boolean hasPermission = scopedGrantService.hasPermission(workspaceMemberId, projectId, environmentId, permission);
        return ResponseEntity.ok(Map.of("hasPermission", hasPermission));
    }
}

package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.JitAccessService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jit")
@RequiredArgsConstructor
@Tag(name = "JIT Access", description = "Just-in-time elevated access requests")
public class JitController {

    private final JitAccessService jitAccessService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @PostMapping("/request")
    @Operation(summary = "Request JIT access (1–24h) with justification")
    public ResponseEntity<Map<String, Object>> request(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        if (body.get("workspaceId") == null || body.get("environmentId") == null) {
            throw new IllegalArgumentException("workspaceId and environmentId are required");
        }
        Long workspaceId = Long.valueOf(body.get("workspaceId").toString());
        Long environmentId = Long.valueOf(body.get("environmentId").toString());
        Object justificationValue = body.get("justification");
        String justification = justificationValue == null ? "" : justificationValue.toString().trim();
        if (justification.isBlank()) {
            throw new IllegalArgumentException("A justification is required for JIT access");
        }
        int hours = body.get("durationHours") != null
                ? Integer.parseInt(body.get("durationHours").toString()) : 2;
        Map<String, Object> result = jitAccessService.requestAccess(workspaceId, environmentId, justification, hours);
        auditLogService.logEvent(securityService.getCurrentUser(), "JIT_REQUEST", "ENVIRONMENT",
                environmentId.toString(), true, request, justification, workspaceId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = jitAccessService.approve(id);
        auditLogService.logEvent(securityService.getCurrentUser(), "JIT_APPROVE", "JIT",
                id.toString(), true, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<Map<String, Object>> deny(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = jitAccessService.deny(id);
        auditLogService.logEvent(securityService.getCurrentUser(), "JIT_DENY", "JIT",
                id.toString(), true, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(jitAccessService.listForWorkspace(workspaceId));
    }
}

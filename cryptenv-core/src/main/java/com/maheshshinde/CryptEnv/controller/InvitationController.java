package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.InvitationService;
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
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Token-based workspace invitations")
public class InvitationController {

    private final InvitationService invitationService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @PostMapping("/api/workspaces/{workspaceId}/invitations")
    @Operation(summary = "Create email-locked invitation (7-day expiry)")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable Long workspaceId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Role role = Role.valueOf(body.getOrDefault("role", "DEVELOPER").toUpperCase());
        Map<String, Object> result = invitationService.createInvitation(workspaceId, body.get("email"), role);
        auditLogService.logEvent(securityService.getCurrentUser(), "INVITATION_CREATE", "WORKSPACE",
                workspaceId.toString(), true, request, "email=" + body.get("email"), workspaceId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/workspaces/{workspaceId}/invitations")
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(invitationService.listPending(workspaceId));
    }

    @PostMapping("/api/invitations/accept")
    @Operation(summary = "Accept invitation with raw token (must match current user email)")
    public ResponseEntity<Map<String, Object>> accept(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Map<String, Object> result = invitationService.acceptInvitation(body.get("token"));
        auditLogService.logEvent(securityService.getCurrentUser(), "INVITATION_ACCEPT", "WORKSPACE",
                String.valueOf(result.get("workspaceId")), true, request);
        return ResponseEntity.ok(result);
    }
}

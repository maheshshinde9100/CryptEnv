package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.WorkspaceCreateDto;
import com.maheshshinde.CryptEnv.dto.WorkspaceResponseDto;
import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.WorkspaceService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspaces", description = "Workspace management APIs")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @PostMapping
    @Operation(summary = "Create a new workspace")
    public ResponseEntity<WorkspaceResponseDto> createWorkspace(@Valid @RequestBody WorkspaceCreateDto createDto,
                                                                  HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_WRITE);
        WorkspaceResponseDto workspaceResponse = workspaceService.createWorkspace(createDto);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_CREATE",
                "WORKSPACE",
                workspaceResponse.getId().toString(),
                true,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workspace by ID")
    public ResponseEntity<WorkspaceResponseDto> getWorkspaceById(@PathVariable Long id,
                                                                 HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_READ);
        WorkspaceResponseDto workspaceResponse = workspaceService.getWorkspaceById(id);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_ACCESS",
                "WORKSPACE",
                id.toString(),
                true,
                request
        );
        return ResponseEntity.ok(workspaceResponse);
    }

    @GetMapping
    @Operation(summary = "Get all workspaces for current user")
    public ResponseEntity<List<WorkspaceResponseDto>> getUserWorkspaces() {
        securityService.checkPermission(Permission.WORKSPACE_READ);
        List<WorkspaceResponseDto> workspaces = workspaceService.getUserWorkspaces();
        return ResponseEntity.ok(workspaces);
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Invite a member to workspace with a scoped role")
    public ResponseEntity<WorkspaceResponseDto> inviteMember(
            @PathVariable Long id,
            @RequestParam String email,
            @RequestParam(required = false, defaultValue = "DEVELOPER") String role,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_MANAGE_MEMBERS);
        com.maheshshinde.CryptEnv.model.Role assignedRole =
                com.maheshshinde.CryptEnv.model.Role.valueOf(role.toUpperCase());
        WorkspaceResponseDto workspaceResponse = workspaceService.inviteMember(id, email, assignedRole);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_MEMBER_ADD",
                "WORKSPACE",
                id.toString(),
                true,
                request,
                "Invited user: " + email + " as " + assignedRole
        );
        return ResponseEntity.ok(workspaceResponse);
    }

    @PutMapping("/{id}/members/{userId}/role")
    @Operation(summary = "Change a member's workspace-scoped role")
    public ResponseEntity<WorkspaceResponseDto> changeMemberRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestParam String role,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_MANAGE_MEMBERS);
        com.maheshshinde.CryptEnv.model.Role newRole =
                com.maheshshinde.CryptEnv.model.Role.valueOf(role.toUpperCase());
        WorkspaceResponseDto workspaceResponse = workspaceService.changeMemberRole(id, userId, newRole);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_MEMBER_ROLE_CHANGE",
                "WORKSPACE",
                id.toString(),
                true,
                request,
                "userId=" + userId + " role=" + newRole
        );
        return ResponseEntity.ok(workspaceResponse);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove a member from workspace")
    public ResponseEntity<WorkspaceResponseDto> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_MANAGE_MEMBERS);
        WorkspaceResponseDto workspaceResponse = workspaceService.removeMember(id, userId);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_MEMBER_REMOVE",
                "WORKSPACE",
                id.toString(),
                true,
                request,
                "Removed userId=" + userId
        );
        return ResponseEntity.ok(workspaceResponse);
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Leave a workspace (non-owners)")
    public ResponseEntity<WorkspaceResponseDto> leaveWorkspace(
            @PathVariable Long id,
            HttpServletRequest request) {
        WorkspaceResponseDto workspaceResponse = workspaceService.leaveWorkspace(id);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_LEAVE",
                "WORKSPACE",
                id.toString(),
                true,
                request
        );
        return ResponseEntity.ok(workspaceResponse);
    }

    @PutMapping("/{id}/encryption-key")
    @Operation(summary = "Update workspace encryption key (owner only)")
    public ResponseEntity<WorkspaceResponseDto> updateWorkspaceEncryptionKey(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_WRITE);
        String newKey = body.get("workspaceEncryptionKey");
        WorkspaceResponseDto workspaceResponse = workspaceService.updateWorkspaceEncryptionKey(id, newKey);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_KEY_UPDATE",
                "WORKSPACE",
                id.toString(),
                true,
                request
        );
        return ResponseEntity.ok(workspaceResponse);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename or update workspace (owner only)")
    public ResponseEntity<WorkspaceResponseDto> renameWorkspace(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_WRITE);
        String newName = body.get("name");
        String newDescription = body.get("description");
        WorkspaceResponseDto workspaceResponse = workspaceService.renameWorkspace(id, newName, newDescription);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_UPDATE",
                "WORKSPACE",
                id.toString(),
                true,
                request
        );
        return ResponseEntity.ok(workspaceResponse);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a workspace")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable Long id, HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_DELETE);
        workspaceService.deleteWorkspace(id);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_DELETE",
                "WORKSPACE",
                id.toString(),
                true,
                request
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/transfer-ownership")
    @Operation(summary = "Transfer workspace ownership (sole-owner invariant)")
    public ResponseEntity<WorkspaceResponseDto> transferOwnership(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Long> body,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_MANAGE_MEMBERS);
        WorkspaceResponseDto dto = workspaceService.transferOwnership(id, body.get("newOwnerUserId"));
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_OWNERSHIP_TRANSFER",
                "WORKSPACE",
                id.toString(),
                true,
                request,
                "newOwnerUserId=" + body.get("newOwnerUserId")
        );
        return ResponseEntity.ok(dto);
    }
}

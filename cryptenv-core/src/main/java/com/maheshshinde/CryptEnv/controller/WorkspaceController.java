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
    @Operation(summary = "Invite a member to workspace")
    public ResponseEntity<WorkspaceResponseDto> inviteMember(
            @PathVariable Long id,
            @RequestParam String email,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_MANAGE_MEMBERS);
        WorkspaceResponseDto workspaceResponse = workspaceService.inviteMember(id, email);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "WORKSPACE_MEMBER_ADD",
                "WORKSPACE",
                id.toString(),
                true,
                request,
                "Invited user: " + email
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
}

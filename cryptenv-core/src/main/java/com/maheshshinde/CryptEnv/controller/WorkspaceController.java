package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.WorkspaceCreateDto;
import com.maheshshinde.CryptEnv.dto.WorkspaceResponseDto;
import com.maheshshinde.CryptEnv.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @PostMapping
    @Operation(summary = "Create a new workspace")
    public ResponseEntity<WorkspaceResponseDto> createWorkspace(@Valid @RequestBody WorkspaceCreateDto createDto) {
        WorkspaceResponseDto workspaceResponse = workspaceService.createWorkspace(createDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workspace by ID")
    public ResponseEntity<WorkspaceResponseDto> getWorkspaceById(@PathVariable Long id) {
        WorkspaceResponseDto workspaceResponse = workspaceService.getWorkspaceById(id);
        return ResponseEntity.ok(workspaceResponse);
    }

    @GetMapping
    @Operation(summary = "Get all workspaces for current user")
    public ResponseEntity<List<WorkspaceResponseDto>> getUserWorkspaces() {
        List<WorkspaceResponseDto> workspaces = workspaceService.getUserWorkspaces();
        return ResponseEntity.ok(workspaces);
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Invite a member to workspace")
    public ResponseEntity<WorkspaceResponseDto> inviteMember(
            @PathVariable Long id,
            @RequestParam String email) {
        WorkspaceResponseDto workspaceResponse = workspaceService.inviteMember(id, email);
        return ResponseEntity.ok(workspaceResponse);
    }
}

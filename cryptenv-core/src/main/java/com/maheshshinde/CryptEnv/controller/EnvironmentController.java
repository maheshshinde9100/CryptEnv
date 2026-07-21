package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.EnvironmentCreateDto;
import com.maheshshinde.CryptEnv.dto.EnvironmentResponseDto;
import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.EnvironmentService;
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
@RequestMapping("/api/environments")
@RequiredArgsConstructor
@Tag(name = "Environments", description = "Environment management APIs")
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @PostMapping
    @Operation(summary = "Create a new environment")
    public ResponseEntity<EnvironmentResponseDto> createEnvironment(@Valid @RequestBody EnvironmentCreateDto createDto,
                                                                       HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_WRITE);
        EnvironmentResponseDto environmentResponse = environmentService.createEnvironment(createDto);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "ENVIRONMENT_CREATE",
                "ENVIRONMENT",
                environmentResponse.getId().toString(),
                true,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(environmentResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get environment by ID")
    public ResponseEntity<EnvironmentResponseDto> getEnvironmentById(@PathVariable Long id,
                                                                     HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_READ);
        EnvironmentResponseDto environmentResponse = environmentService.getEnvironmentById(id);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "ENVIRONMENT_ACCESS",
                "ENVIRONMENT",
                id.toString(),
                true,
                request
        );
        return ResponseEntity.ok(environmentResponse);
    }

    @GetMapping("/workspace/{workspaceId}")
    @Operation(summary = "Get all environments for a workspace")
    public ResponseEntity<List<EnvironmentResponseDto>> getEnvironmentsByWorkspace(@PathVariable Long workspaceId) {
        securityService.checkPermission(Permission.WORKSPACE_READ);
        List<EnvironmentResponseDto> environments = environmentService.getEnvironmentsByWorkspace(workspaceId);
        return ResponseEntity.ok(environments);
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle environment active status")
    public ResponseEntity<EnvironmentResponseDto> toggleEnvironmentStatus(@PathVariable Long id,
                                                                          HttpServletRequest request) {
        securityService.checkPermission(Permission.WORKSPACE_WRITE);
        EnvironmentResponseDto environmentResponse = environmentService.toggleEnvironmentStatus(id);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "ENVIRONMENT_UPDATE",
                "ENVIRONMENT",
                id.toString(),
                true,
                request
        );
        return ResponseEntity.ok(environmentResponse);
    }
}

package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.ProjectDto;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.ProjectService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Workspace → Project layer")
public class ProjectController {

    private final ProjectService projectService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @PostMapping
    @Operation(summary = "Create a project in a workspace")
    public ResponseEntity<ProjectDto> create(
            @PathVariable Long workspaceId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        ProjectDto dto = projectService.create(workspaceId, body.get("name"), body.get("description"));
        auditLogService.logEvent(securityService.getCurrentUser(), "PROJECT_CREATE", "PROJECT",
                dto.getId().toString(), true, request, null, workspaceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<ProjectDto>> list(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(projectService.list(workspaceId));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectDto> get(@PathVariable Long workspaceId, @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.get(workspaceId, projectId));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            HttpServletRequest request) {
        projectService.delete(workspaceId, projectId);
        auditLogService.logEvent(securityService.getCurrentUser(), "PROJECT_DELETE", "PROJECT",
                projectId.toString(), true, request, null, workspaceId);
        return ResponseEntity.noContent().build();
    }
}

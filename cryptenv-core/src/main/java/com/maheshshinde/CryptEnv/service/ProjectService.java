package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.ProjectDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Project;
import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.ProjectRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceService workspaceService;
    private final SecurityService securityService;

    @Transactional
    public ProjectDto create(Long workspaceId, String name, String description) {
        User user = securityService.getCurrentUser();
        Workspace workspace = requireWorkspace(workspaceId);
        requireAdmin(workspace, user);
        if (projectRepository.existsByWorkspaceIdAndName(workspaceId, name.trim())) {
            throw new ResourceAlreadyExistsException("Project already exists: " + name);
        }
        Project project = Project.builder()
                .workspace(workspace)
                .name(name.trim())
                .description(description)
                .build();
        return toDto(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> list(Long workspaceId) {
        User user = securityService.getCurrentUser();
        Workspace workspace = requireWorkspace(workspaceId);
        requireAccess(workspace, user);
        return projectRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectDto get(Long workspaceId, Long projectId) {
        User user = securityService.getCurrentUser();
        Workspace workspace = requireWorkspace(workspaceId);
        requireAccess(workspace, user);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        return toDto(project);
    }

    @Transactional
    public void delete(Long workspaceId, Long projectId) {
        User user = securityService.getCurrentUser();
        Workspace workspace = requireWorkspace(workspaceId);
        requireAdmin(workspace, user);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if ("Default".equalsIgnoreCase(project.getName())) {
            throw new IllegalArgumentException("Cannot delete the Default project");
        }
        projectRepository.delete(project);
    }

    @Transactional
    public Project getOrCreateDefault(Workspace workspace) {
        return projectRepository.findByWorkspaceIdAndName(workspace.getId(), "Default")
                .orElseGet(() -> projectRepository.save(Project.builder()
                        .workspace(workspace)
                        .name("Default")
                        .description("Auto-created default project")
                        .build()));
    }

    private Workspace requireWorkspace(Long id) {
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }

    private void requireAccess(Workspace workspace, User user) {
        if (workspaceService.getEffectiveRole(workspace, user) == null) {
            throw new RuntimeException("Access denied");
        }
    }

    private void requireAdmin(Workspace workspace, User user) {
        Role role = workspaceService.getEffectiveRole(workspace, user);
        if (role != Role.OWNER && role != Role.ADMIN) {
            throw new RuntimeException("Access denied: admin required");
        }
    }

    private ProjectDto toDto(Project p) {
        return ProjectDto.builder()
                .id(p.getId())
                .workspaceId(p.getWorkspace().getId())
                .name(p.getName())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}

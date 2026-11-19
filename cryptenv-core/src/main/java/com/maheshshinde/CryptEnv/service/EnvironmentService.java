package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.EnvironmentCreateDto;
import com.maheshshinde.CryptEnv.dto.EnvironmentResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.*;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.ProjectRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final WorkspaceService workspaceService;
    private final SecurityService securityService;

    private void validateWorkspaceAccess(Workspace workspace, User user) {
        if (workspaceService.getEffectiveRole(workspace, user) == null) {
            throw new RuntimeException("Access denied: You do not have permission to this workspace");
        }
    }

    private void requireAdmin(Workspace workspace, User user) {
        Role role = workspaceService.getEffectiveRole(workspace, user);
        if (role != Role.OWNER && role != Role.ADMIN) {
            throw new RuntimeException("Access denied: Only workspace owner/admin can create or modify environments");
        }
    }

    @Transactional
    public EnvironmentResponseDto createEnvironment(EnvironmentCreateDto createDto) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(createDto.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + createDto.getWorkspaceId()));
        requireAdmin(workspace, currentUser);

        Environment.EnvironmentType type = createDto.getName();
        if (type == Environment.EnvironmentType.CUSTOM
                && (createDto.getLabel() == null || createDto.getLabel().isBlank())) {
            throw new IllegalArgumentException("CUSTOM environments require a label");
        }

        if (type != Environment.EnvironmentType.CUSTOM
                && environmentRepository.findByWorkspaceIdAndName(createDto.getWorkspaceId(), type).isPresent()) {
            throw new ResourceAlreadyExistsException(
                    "Environment " + type + " already exists for workspace: " + workspace.getName()
            );
        }

        Project project;
        if (createDto.getProjectId() != null) {
            project = projectRepository.findByIdAndWorkspaceId(createDto.getProjectId(), workspace.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        } else {
            project = projectService.getOrCreateDefault(workspace);
        }

        Environment environment = Environment.builder()
                .name(type)
                .label(createDto.getLabel())
                .workspace(workspace)
                .project(project)
                .isActive(true)
                .isProtected(Boolean.TRUE.equals(createDto.getIsProtected())
                        || type == Environment.EnvironmentType.PRODUCTION)
                .build();

        return mapToResponseDto(environmentRepository.save(environment));
    }

    @Transactional(readOnly = true)
    public EnvironmentResponseDto getEnvironmentById(Long id) {
        User currentUser = securityService.getCurrentUser();
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));
        validateWorkspaceAccess(environment.getWorkspace(), currentUser);
        return mapToResponseDto(environment);
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponseDto> getEnvironmentsByWorkspace(Long workspaceId) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        validateWorkspaceAccess(workspace, currentUser);

        return environmentRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EnvironmentResponseDto setProtected(Long id, boolean isProtected) {
        User currentUser = securityService.getCurrentUser();
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));
        requireAdmin(environment.getWorkspace(), currentUser);
        environment.setIsProtected(isProtected);
        return mapToResponseDto(environmentRepository.save(environment));
    }

    @Transactional
    public EnvironmentResponseDto toggleEnvironmentStatus(Long id) {
        User currentUser = securityService.getCurrentUser();
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));
        requireAdmin(environment.getWorkspace(), currentUser);
        environment.setIsActive(!environment.getIsActive());
        return mapToResponseDto(environmentRepository.save(environment));
    }

    @Transactional
    public void deleteEnvironment(Long id) {
        User currentUser = securityService.getCurrentUser();
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));
        requireAdmin(environment.getWorkspace(), currentUser);
        environmentRepository.delete(environment);
    }

    private EnvironmentResponseDto mapToResponseDto(Environment environment) {
        return EnvironmentResponseDto.builder()
                .id(environment.getId())
                .name(environment.getName())
                .label(environment.getLabel())
                .displayName(environment.displayName())
                .workspaceId(environment.getWorkspace().getId())
                .workspaceName(environment.getWorkspace().getName())
                .projectId(environment.getProject() != null ? environment.getProject().getId() : null)
                .projectName(environment.getProject() != null ? environment.getProject().getName() : null)
                .isActive(environment.getIsActive())
                .isProtected(environment.getIsProtected())
                .createdAt(environment.getCreatedAt())
                .updatedAt(environment.getUpdatedAt())
                .build();
    }
}

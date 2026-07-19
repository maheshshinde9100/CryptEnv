package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.EnvironmentCreateDto;
import com.maheshshinde.CryptEnv.dto.EnvironmentResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
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

    @Transactional
    public EnvironmentResponseDto createEnvironment(EnvironmentCreateDto createDto) {
        Workspace workspace = workspaceRepository.findById(createDto.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + createDto.getWorkspaceId()));

        if (environmentRepository.findByWorkspaceIdAndName(createDto.getWorkspaceId(), createDto.getName()).isPresent()) {
            throw new ResourceAlreadyExistsException(
                    "Environment " + createDto.getName() + " already exists for workspace: " + workspace.getName()
            );
        }

        Environment environment = Environment.builder()
                .name(createDto.getName())
                .workspace(workspace)
                .isActive(true)
                .build();

        Environment savedEnvironment = environmentRepository.save(environment);
        return mapToResponseDto(savedEnvironment);
    }

    @Transactional(readOnly = true)
    public EnvironmentResponseDto getEnvironmentById(Long id) {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));
        return mapToResponseDto(environment);
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponseDto> getEnvironmentsByWorkspace(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));

        return environmentRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EnvironmentResponseDto toggleEnvironmentStatus(Long id) {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));

        environment.setIsActive(!environment.getIsActive());
        Environment savedEnvironment = environmentRepository.save(environment);
        return mapToResponseDto(savedEnvironment);
    }

    private EnvironmentResponseDto mapToResponseDto(Environment environment) {
        return EnvironmentResponseDto.builder()
                .id(environment.getId())
                .name(environment.getName())
                .workspaceId(environment.getWorkspace().getId())
                .workspaceName(environment.getWorkspace().getName())
                .isActive(environment.getIsActive())
                .createdAt(environment.getCreatedAt())
                .updatedAt(environment.getUpdatedAt())
                .build();
    }
}

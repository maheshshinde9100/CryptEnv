package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.WorkspaceCreateDto;
import com.maheshshinde.CryptEnv.dto.WorkspaceResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import com.maheshshinde.CryptEnv.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkspaceResponseDto createWorkspace(WorkspaceCreateDto createDto) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User owner = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (workspaceRepository.existsByName(createDto.getName())) {
            throw new ResourceAlreadyExistsException("Workspace name already exists: " + createDto.getName());
        }

        Workspace workspace = Workspace.builder()
                .name(createDto.getName())
                .description(createDto.getDescription())
                .owner(owner)
                .build();

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapToResponseDto(savedWorkspace);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponseDto getWorkspaceById(Long id) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + id));
        return mapToResponseDto(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponseDto> getUserWorkspaces() {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Workspace> ownedWorkspaces = workspaceRepository.findByOwnerId(userPrincipal.getId());
        List<Workspace> memberWorkspaces = workspaceRepository.findByMembersId(userPrincipal.getId());
        
        return ownedWorkspaces.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public WorkspaceResponseDto inviteMember(Long workspaceId, String email) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));

        User userToInvite = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (workspace.getMembers().contains(userToInvite)) {
            throw new ResourceAlreadyExistsException("User is already a member of this workspace");
        }

        workspace.getMembers().add(userToInvite);
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapToResponseDto(savedWorkspace);
    }

    private WorkspaceResponseDto mapToResponseDto(Workspace workspace) {
        return WorkspaceResponseDto.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .ownerId(workspace.getOwner().getId())
                .ownerUsername(workspace.getOwner().getUsername())
                .memberUsernames(workspace.getMembers().stream()
                        .map(User::getUsername)
                        .collect(Collectors.toSet()))
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }

    @Transactional
    public void deleteWorkspace(Long id) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + id));
        workspaceRepository.delete(workspace);
    }
}

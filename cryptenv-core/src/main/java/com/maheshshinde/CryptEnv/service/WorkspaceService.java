package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.WorkspaceCreateDto;
import com.maheshshinde.CryptEnv.dto.WorkspaceResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final SecurityService securityService;

    @Transactional
    public WorkspaceResponseDto createWorkspace(WorkspaceCreateDto createDto) {
        User currentUser = securityService.getCurrentUser();
        User owner = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (workspaceRepository.existsByName(createDto.getName())) {
            throw new ResourceAlreadyExistsException("Workspace name already exists: " + createDto.getName());
        }

        String encryptedWorkspaceKey = null;
        if (createDto.getWorkspaceEncryptionKey() != null && !createDto.getWorkspaceEncryptionKey().isBlank()) {
            encryptedWorkspaceKey = encryptionService.encryptWorkspaceKey(createDto.getWorkspaceEncryptionKey().trim());
        }

        Workspace workspace = Workspace.builder()
                .name(createDto.getName())
                .description(createDto.getDescription())
                .owner(owner)
                .workspaceEncryptionKey(encryptedWorkspaceKey)
                .build();

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapToResponseDto(savedWorkspace);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponseDto getWorkspaceById(Long id) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + id));
        validateWorkspaceAccess(workspace, currentUser);
        return mapToResponseDto(workspace);
    }

    @Transactional(readOnly = true)
    public String getDecryptedWorkspaceKey(Long workspaceId) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        validateWorkspaceAccess(workspace, currentUser);

        if (workspace.getWorkspaceEncryptionKey() == null) {
            throw new ResourceNotFoundException("Workspace encryption key not set for this workspace. Please update workspace settings.");
        }

        return encryptionService.decryptWorkspaceKey(workspace.getWorkspaceEncryptionKey());
    }

    @Transactional(readOnly = true)
    public Workspace getWorkspaceEntityByIdAndOwner(Long workspaceId, Long ownerId) {
        return workspaceRepository.findById(workspaceId)
                .filter(w -> w.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or access denied"));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponseDto> getUserWorkspaces() {
        User currentUser = securityService.getCurrentUser();
        List<Workspace> ownedWorkspaces = workspaceRepository.findByOwnerId(currentUser.getId());
        List<Workspace> memberWorkspaces = workspaceRepository.findByMembersId(currentUser.getId());

        java.util.Set<Workspace> allWorkspaces = new java.util.LinkedHashSet<>();
        allWorkspaces.addAll(ownedWorkspaces);
        allWorkspaces.addAll(memberWorkspaces);

        return allWorkspaces.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public WorkspaceResponseDto inviteMember(Long workspaceId, String email) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        validateWorkspaceOwner(workspace, currentUser);

        User userToInvite = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (workspace.getMembers().contains(userToInvite)) {
            throw new ResourceAlreadyExistsException("User is already a member of this workspace");
        }

        workspace.getMembers().add(userToInvite);
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapToResponseDto(savedWorkspace);
    }

    @Transactional
    public WorkspaceResponseDto updateWorkspaceEncryptionKey(Long workspaceId, String newWorkspaceKey) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        validateWorkspaceOwner(workspace, currentUser);

        if (newWorkspaceKey == null || newWorkspaceKey.trim().length() < 16) {
            throw new IllegalArgumentException("Workspace encryption key must be at least 16 characters");
        }

        workspace.setWorkspaceEncryptionKey(encryptionService.encryptWorkspaceKey(newWorkspaceKey.trim()));
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapToResponseDto(savedWorkspace);
    }

    @Transactional
    public WorkspaceResponseDto renameWorkspace(Long workspaceId, String newName, String newDescription) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        validateWorkspaceOwner(workspace, currentUser);

        if (newName != null && !newName.trim().isBlank() && !newName.equals(workspace.getName())) {
            if (workspaceRepository.existsByName(newName.trim())) {
                throw new ResourceAlreadyExistsException("Workspace name already exists: " + newName);
            }
            workspace.setName(newName.trim());
        }
        if (newDescription != null) {
            workspace.setDescription(newDescription);
        }

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapToResponseDto(savedWorkspace);
    }

    private void validateWorkspaceAccess(Workspace workspace, User user) {
        boolean isOwner = workspace.getOwner().getId().equals(user.getId());
        boolean isMember = workspace.getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        if (!isOwner && !isMember) {
            throw new RuntimeException("Access denied: You do not have permission to access this workspace");
        }
    }

    private void validateWorkspaceOwner(Workspace workspace, User user) {
        if (!workspace.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: Only workspace owner can perform this action");
        }
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
                .hasEncryptionKey(workspace.getWorkspaceEncryptionKey() != null)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }

    @Transactional
    public void deleteWorkspace(Long id) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + id));
        validateWorkspaceOwner(workspace, currentUser);
        workspaceRepository.delete(workspace);
    }
}

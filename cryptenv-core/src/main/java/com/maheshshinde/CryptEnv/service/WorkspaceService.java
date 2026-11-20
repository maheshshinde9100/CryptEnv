package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.WorkspaceCreateDto;
import com.maheshshinde.CryptEnv.dto.WorkspaceMemberDto;
import com.maheshshinde.CryptEnv.dto.WorkspaceResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.model.WorkspaceMember;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceMemberRepository;
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
    private final WorkspaceMemberRepository workspaceMemberRepository;
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
        return mapToResponseDto(savedWorkspace, currentUser);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponseDto getWorkspaceById(Long id) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + id));
        validateWorkspaceAccess(workspace, currentUser);
        return mapToResponseDto(workspace, currentUser);
    }

    @Transactional(readOnly = true)
    public String getDecryptedWorkspaceKey(Long workspaceId) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        validateWorkspaceAccess(workspace, currentUser);

        return decryptKey(workspace);
    }

    /** Key access for trusted system jobs (rotation/expiry) that run without a security context. */
    @Transactional(readOnly = true)
    public String getDecryptedWorkspaceKeyForSystem(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        return decryptKey(workspace);
    }

    private String decryptKey(Workspace workspace) {
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
        List<Workspace> memberWorkspaces = workspaceRepository.findByMembershipsUserId(currentUser.getId());

        java.util.Set<Workspace> allWorkspaces = new java.util.LinkedHashSet<>();
        allWorkspaces.addAll(ownedWorkspaces);
        allWorkspaces.addAll(memberWorkspaces);

        return allWorkspaces.stream()
                .map(w -> mapToResponseDto(w, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional
    public WorkspaceResponseDto inviteMember(Long workspaceId, String email, Role role) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        requireWorkspaceAdmin(workspace, currentUser);

        Role assignedRole = role != null ? role : Role.DEVELOPER;
        if (assignedRole == Role.OWNER) {
            throw new IllegalArgumentException("Cannot invite as OWNER; transfer ownership instead");
        }

        User userToInvite = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (workspace.getOwner().getId().equals(userToInvite.getId())) {
            throw new ResourceAlreadyExistsException("User is already the workspace owner");
        }
        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userToInvite.getId())) {
            throw new ResourceAlreadyExistsException("User is already a member of this workspace");
        }

        WorkspaceMember membership = WorkspaceMember.builder()
                .workspace(workspace)
                .user(userToInvite)
                .role(assignedRole)
                .build();
        workspace.getMemberships().add(membership);
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapToResponseDto(savedWorkspace, currentUser);
    }

    @Transactional
    public WorkspaceResponseDto changeMemberRole(Long workspaceId, Long userId, Role newRole) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        requireWorkspaceAdmin(workspace, currentUser);

        if (newRole == Role.OWNER) {
            throw new IllegalArgumentException("Cannot assign OWNER via role change; transfer ownership instead");
        }

        WorkspaceMember membership = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in workspace"));
        membership.setRole(newRole);
        workspaceMemberRepository.save(membership);
        return mapToResponseDto(workspace, currentUser);
    }

    @Transactional
    public WorkspaceResponseDto removeMember(Long workspaceId, Long userId) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        requireWorkspaceAdmin(workspace, currentUser);

        if (workspace.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("Cannot remove the workspace owner");
        }

        workspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspaceId, userId);
        workspace.getMemberships().removeIf(m -> m.getUser().getId().equals(userId));
        return mapToResponseDto(workspace, currentUser);
    }

    @Transactional
    public WorkspaceResponseDto leaveWorkspace(Long workspaceId) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));

        if (workspace.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Owner cannot leave; transfer ownership or delete the workspace");
        }

        workspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspaceId, currentUser.getId());
        return mapToResponseDto(workspace, currentUser);
    }

    /** Resolves the caller's effective role in this workspace (OWNER if owner, else membership role). */
    @Transactional(readOnly = true)
    public Role getEffectiveRole(Workspace workspace, User user) {
        if (workspace.getOwner().getId().equals(user.getId())) {
            return Role.OWNER;
        }
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .map(WorkspaceMember::getRole)
                .orElse(null);
    }

    @Transactional
    public WorkspaceResponseDto updateWorkspaceEncryptionKey(Long workspaceId, String newWorkspaceKey) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        requireWorkspaceOwner(workspace, currentUser);

        if (newWorkspaceKey == null || newWorkspaceKey.trim().length() < 16) {
            throw new IllegalArgumentException("Workspace encryption key must be at least 16 characters");
        }

        workspace.setWorkspaceEncryptionKey(encryptionService.encryptWorkspaceKey(newWorkspaceKey.trim()));
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapToResponseDto(savedWorkspace, currentUser);
    }

    @Transactional
    public WorkspaceResponseDto renameWorkspace(Long workspaceId, String newName, String newDescription) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        requireWorkspaceAdmin(workspace, currentUser);

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
        return mapToResponseDto(savedWorkspace, currentUser);
    }

    private void validateWorkspaceAccess(Workspace workspace, User user) {
        Role role = getEffectiveRole(workspace, user);
        if (role == null) {
            throw new RuntimeException("Access denied: You do not have permission to access this workspace");
        }
    }

    private void requireWorkspaceOwner(Workspace workspace, User user) {
        if (!workspace.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: Only workspace owner can perform this action");
        }
    }

    private void requireWorkspaceAdmin(Workspace workspace, User user) {
        Role role = getEffectiveRole(workspace, user);
        if (role != Role.OWNER && role != Role.ADMIN) {
            throw new RuntimeException("Access denied: Only workspace owner or admin can perform this action");
        }
    }

    private WorkspaceResponseDto mapToResponseDto(Workspace workspace, User currentUser) {
        Role currentRole = getEffectiveRole(workspace, currentUser);
        return WorkspaceResponseDto.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .ownerId(workspace.getOwner().getId())
                .ownerUsername(workspace.getOwner().getUsername())
                .memberUsernames(workspace.getMemberships().stream()
                        .map(m -> m.getUser().getUsername())
                        .collect(Collectors.toSet()))
                .members(workspace.getMemberships().stream()
                        .map(m -> WorkspaceMemberDto.builder()
                                .userId(m.getUser().getId())
                                .username(m.getUser().getUsername())
                                .email(m.getUser().getEmail())
                                .role(m.getRole())
                                .build())
                        .collect(Collectors.toSet()))
                .currentUserRole(currentRole)
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
        requireWorkspaceOwner(workspace, currentUser);
        workspaceRepository.delete(workspace);
    }

    /**
     * Transfer ownership to an existing member (or any user). Former owner becomes ADMIN member.
     * Enforces sole-owner invariant: there is always exactly one owner.
     */
    @Transactional
    public WorkspaceResponseDto transferOwnership(Long workspaceId, Long newOwnerUserId) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        requireWorkspaceOwner(workspace, currentUser);

        User newOwner = userRepository.findById(newOwnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User previousOwner = workspace.getOwner();
        workspace.setOwner(newOwner);

        // Remove new owner from members if present
        workspace.getMemberships().removeIf(m -> m.getUser().getId().equals(newOwnerUserId));
        workspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspaceId, newOwnerUserId);

        // Previous owner becomes ADMIN member (unless same user)
        if (!previousOwner.getId().equals(newOwnerUserId)) {
            WorkspaceMember adminMembership = WorkspaceMember.builder()
                    .workspace(workspace)
                    .user(previousOwner)
                    .role(Role.ADMIN)
                    .build();
            workspace.getMemberships().add(adminMembership);
        }

        Workspace saved = workspaceRepository.save(workspace);
        return mapToResponseDto(saved, currentUser);
    }
}

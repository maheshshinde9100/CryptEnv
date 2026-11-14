package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.*;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceInvitationRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceMemberRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import com.maheshshinde.CryptEnv.security.ApiKeyHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final SecurityService securityService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public Map<String, Object> createInvitation(Long workspaceId, String email, Role role) {
        User current = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        Role effective = workspaceService.getEffectiveRole(workspace, current);
        if (effective != Role.OWNER && effective != Role.ADMIN) {
            throw new RuntimeException("Only owner/admin can invite");
        }
        if (role == Role.OWNER) {
            throw new IllegalArgumentException("Cannot invite as OWNER");
        }

        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String rawToken = HexFormat.of().formatHex(tokenBytes);

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .email(email.trim().toLowerCase())
                .role(role != null ? role : Role.DEVELOPER)
                .tokenHash(ApiKeyHasher.sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .invitedBy(current)
                .build();
        invitationRepository.save(invitation);

        return Map.of(
                "id", invitation.getId(),
                "email", invitation.getEmail(),
                "role", invitation.getRole().name(),
                "expiresAt", invitation.getExpiresAt().toString(),
                "token", rawToken // shown once; email delivery later
        );
    }

    @Transactional
    public Map<String, Object> acceptInvitation(String rawToken) {
        User current = securityService.getCurrentUser();
        String hash = ApiKeyHasher.sha256Hex(rawToken);
        WorkspaceInvitation invitation = invitationRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (invitation.getAcceptedAt() != null) {
            throw new IllegalArgumentException("Invitation already accepted");
        }
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invitation expired");
        }
        if (!invitation.getEmail().equalsIgnoreCase(current.getEmail())) {
            throw new IllegalArgumentException("Invitation is locked to a different email");
        }

        Workspace workspace = invitation.getWorkspace();
        if (!workspace.getOwner().getId().equals(current.getId())
                && !memberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), current.getId())) {
            WorkspaceMember membership = WorkspaceMember.builder()
                    .workspace(workspace)
                    .user(current)
                    .role(invitation.getRole())
                    .build();
            memberRepository.save(membership);
        }

        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        return Map.of(
                "workspaceId", workspace.getId(),
                "workspaceName", workspace.getName(),
                "role", invitation.getRole().name()
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPending(Long workspaceId) {
        User current = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (workspaceService.getEffectiveRole(workspace, current) == null) {
            throw new RuntimeException("Access denied");
        }
        return invitationRepository.findByWorkspaceIdAndAcceptedAtIsNull(workspaceId).stream()
                .map(i -> Map.<String, Object>of(
                        "id", i.getId(),
                        "email", i.getEmail(),
                        "role", i.getRole().name(),
                        "expiresAt", i.getExpiresAt().toString()
                ))
                .collect(Collectors.toList());
    }
}

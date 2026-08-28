package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.*;
import com.maheshshinde.CryptEnv.exception.InvalidCredentialsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import com.maheshshinde.CryptEnv.security.JwtTokenProvider;
import com.maheshshinde.CryptEnv.security.UserPrincipal;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sdk")
@RequiredArgsConstructor
@Tag(name = "SDK", description = "NPM / Node.js SDK specific endpoints for dynamic secret retrieval with client-side decryption")
public class SdkController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretRepository secretRepository;
    private final SecurityService securityService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    @Operation(summary = "[SDK] Login with email+password to get JWT + workspaces list for NPM library")
    public ResponseEntity<SdkLoginResponseDto> sdkLogin(@Valid @RequestBody UserLoginDto loginDto) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginDto.getEmail(),
                            loginDto.getPassword()
                    )
            );

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            String token = tokenProvider.generateToken(authentication);

            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            List<Workspace> ownedWorkspaces = workspaceRepository.findByOwnerId(user.getId());
            List<Workspace> memberWorkspaces = workspaceRepository.findByMembersId(user.getId());
            java.util.Set<Workspace> allWorkspaces = new java.util.LinkedHashSet<>();
            allWorkspaces.addAll(ownedWorkspaces);
            allWorkspaces.addAll(memberWorkspaces);

            List<SdkLoginResponseDto.SdkWorkspaceDto> workspaceDtos = allWorkspaces.stream()
                    .map(workspace -> {
                        List<Environment> envs = environmentRepository.findByWorkspaceId(workspace.getId());
                        List<SdkLoginResponseDto.SdkEnvironmentDto> envDtos = envs.stream()
                                .map(env -> SdkLoginResponseDto.SdkEnvironmentDto.builder()
                                        .id(env.getId())
                                        .name(env.getName().name())
                                        .isActive(env.getIsActive())
                                        .build())
                                .collect(Collectors.toList());

                        return SdkLoginResponseDto.SdkWorkspaceDto.builder()
                                .id(workspace.getId())
                                .name(workspace.getName())
                                .description(workspace.getDescription())
                                .hasEncryptionKey(workspace.getWorkspaceEncryptionKey() != null)
                                .environments(envDtos)
                                .build();
                    })
                    .collect(Collectors.toList());

            SdkLoginResponseDto response = SdkLoginResponseDto.builder()
                    .token(token)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .workspaces(workspaceDtos)
                    .issuedAt(LocalDateTime.now())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EncryptedSecretDto {
        private Long id;
        private String key;
        private String encryptedValue;
        private Long environmentId;
        private Long workspaceId;
        private String description;
        private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @GetMapping("/secrets/encrypted")
    @Operation(summary = "[SDK] Get all secrets in ENCRYPTED form for client-side decryption with workspace key")
    public ResponseEntity<List<EncryptedSecretDto>> getAllEncryptedSecrets(
            @RequestParam(required = false) Long workspaceId) {
        User currentUser = securityService.getCurrentUser();

        List<Secret> secrets;
        if (workspaceId != null) {
            Workspace workspace = workspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
            boolean isOwner = workspace.getOwner().getId().equals(currentUser.getId());
            boolean isMember = workspace.getMembers().stream()
                    .anyMatch(m -> m.getId().equals(currentUser.getId()));
            if (!isOwner && !isMember) {
                throw new RuntimeException("Access denied");
            }
            secrets = secretRepository.findByEnvironmentWorkspaceId(workspaceId);
        } else {
            secrets = secretRepository.findByEnvironmentWorkspaceOwnerOrMemberId(currentUser.getId());
        }

        List<EncryptedSecretDto> dtos = secrets.stream()
                .filter(s -> Boolean.TRUE.equals(s.getEncrypted()))
                .map(s -> EncryptedSecretDto.builder()
                        .id(s.getId())
                        .key(s.getKey())
                        .encryptedValue(s.getEncryptedValue() != null ? s.getEncryptedValue() : s.getValue())
                        .environmentId(s.getEnvironment().getId())
                        .workspaceId(s.getEnvironment().getWorkspace().getId())
                        .description(s.getDescription())
                        .version(s.getVersion())
                        .createdAt(s.getCreatedAt())
                        .updatedAt(s.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping({"/secrets/{key}", "/secrets/encrypted/{key}"})
    @Operation(summary = "[SDK] Get a single secret in ENCRYPTED form by key for client-side decryption")
    public ResponseEntity<EncryptedSecretDto> getEncryptedSecretByKey(@PathVariable String key) {
        User currentUser = securityService.getCurrentUser();

        List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId(key, currentUser.getId());
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("Secret not found with key: " + key);
        }
        Secret s = secrets.get(0);

        if (!Boolean.TRUE.equals(s.getEncrypted())) {
            throw new RuntimeException("Secret is not encrypted - use the regular secrets endpoint");
        }

        EncryptedSecretDto dto = EncryptedSecretDto.builder()
                .id(s.getId())
                .key(s.getKey())
                .encryptedValue(s.getEncryptedValue() != null ? s.getEncryptedValue() : s.getValue())
                .environmentId(s.getEnvironment().getId())
                .workspaceId(s.getEnvironment().getWorkspace().getId())
                .description(s.getDescription())
                .version(s.getVersion())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/workspaces/{workspaceId}/encrypted-secrets-map")
    @Operation(summary = "[SDK] Get a flat encrypted KEY->ENCRYPTED_VALUE map for all secrets in a workspace (client decrypts)")
    public ResponseEntity<Map<String, String>> getEncryptedSecretsMapForWorkspace(@PathVariable Long workspaceId) {
        User currentUser = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        boolean isOwner = workspace.getOwner().getId().equals(currentUser.getId());
        boolean isMember = workspace.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (!isOwner && !isMember) {
            throw new RuntimeException("Access denied to this workspace");
        }

        List<Secret> workspaceSecrets = secretRepository.findByEnvironmentWorkspaceId(workspaceId);

        Map<String, String> map = new HashMap<>();
        for (Secret s : workspaceSecrets) {
            if (Boolean.TRUE.equals(s.getEncrypted())) {
                String encVal = s.getEncryptedValue() != null ? s.getEncryptedValue() : s.getValue();
                if (encVal != null) {
                    map.put(s.getKey(), encVal);
                }
            }
        }
        return ResponseEntity.ok(map);
    }
}

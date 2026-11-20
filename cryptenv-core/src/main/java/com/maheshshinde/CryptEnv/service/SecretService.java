package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.SecretCreateDto;
import com.maheshshinde.CryptEnv.dto.SecretResponseDto;
import com.maheshshinde.CryptEnv.dto.SecretUpdateDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.model.SecretVersion;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import com.maheshshinde.CryptEnv.repository.SecretVersionRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import com.maheshshinde.CryptEnv.security.EncryptionException;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretService {

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final EnvironmentRepository environmentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EncryptionService encryptionService;
    private final SecurityService securityService;
    private final WorkspaceService workspaceService;
    private final JitAccessService jitAccessService;

    private void validateEnvironmentOwnership(Environment environment, User user) {
        Long ownerId = environment.getWorkspace().getOwner().getId();
        boolean isOwner = ownerId.equals(user.getId());
        boolean isMember = environment.getWorkspace().getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()));
        if (!isOwner && !isMember) {
            throw new RuntimeException("Access denied: You do not have permission to access secrets in this workspace");
        }
    }

    /**
     * Write access: workspace member with a writable role; protected environments additionally
     * require OWNER/ADMIN or an approved JIT grant. System jobs (no security context) bypass.
     */
    private void requireWriteAccess(Environment environment) {
        User user = securityService.getCurrentUser();
        if (user == null) {
            return;
        }
        validateEnvironmentOwnership(environment, user);
        Role role = workspaceService.getEffectiveRole(environment.getWorkspace(), user);
        if (role == Role.VIEWER || role == Role.AUDITOR) {
            throw new RuntimeException("Your workspace role is read-only and cannot modify secrets");
        }
        if (Boolean.TRUE.equals(environment.getIsProtected())
                && role != Role.OWNER && role != Role.ADMIN
                && !jitAccessService.hasActiveGrant(environment.getId(), user.getId())) {
            throw new RuntimeException(
                    "Environment is protected. Request JIT access or ask a workspace owner/admin to change it.");
        }
    }

    private String getWorkspaceEncryptionKeyForEnv(Environment environment) {
        Long workspaceId = environment.getWorkspace().getId();
        if (securityService.getCurrentUser() == null) {
            return workspaceService.getDecryptedWorkspaceKeyForSystem(workspaceId);
        }
        return workspaceService.getDecryptedWorkspaceKey(workspaceId);
    }

    private Environment getOrCreateDefaultEnvironment(User user) {
        List<Workspace> ownedWorkspaces = workspaceRepository.findByOwnerId(user.getId());
        List<Workspace> memberWorkspaces = workspaceRepository.findByMembershipsUserId(user.getId());
        java.util.Set<Workspace> allWorkspaces = new java.util.LinkedHashSet<>();
        allWorkspaces.addAll(ownedWorkspaces);
        allWorkspaces.addAll(memberWorkspaces);

        Workspace finalWorkspace;
        if (allWorkspaces.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No workspace found. Please create a workspace first at POST /api/workspaces with a workspaceEncryptionKey."
            );
        } else {
            finalWorkspace = allWorkspaces.iterator().next();
        }

        if (finalWorkspace.getWorkspaceEncryptionKey() == null) {
            throw new ResourceNotFoundException(
                    "Workspace '" + finalWorkspace.getName() + "' does not have an encryption key set. " +
                    "Set it via PUT /api/workspaces/" + finalWorkspace.getId() + "/encryption-key"
            );
        }

        return environmentRepository.findByWorkspaceIdAndName(finalWorkspace.getId(), Environment.EnvironmentType.DEVELOPMENT)
                .orElseGet(() -> {
                    Environment env = Environment.builder()
                            .name(Environment.EnvironmentType.DEVELOPMENT)
                            .workspace(finalWorkspace)
                            .isActive(true)
                            .build();
                    return environmentRepository.save(env);
                });
    }

    @Transactional
    public SecretResponseDto createSecret(SecretCreateDto createDto) {
        User currentUser = securityService.getCurrentUser();
        Environment environment;

        if (createDto.getEnvironmentId() == null) {
            environment = getOrCreateDefaultEnvironment(currentUser);
        } else {
            environment = environmentRepository.findById(createDto.getEnvironmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + createDto.getEnvironmentId()));
            validateEnvironmentOwnership(environment, currentUser);
        }
        requireWriteAccess(environment);

        if (secretRepository.existsByEnvironmentIdAndKey(environment.getId(), createDto.getKey())) {
            throw new ResourceAlreadyExistsException("Secret with key '" + createDto.getKey() + "' already exists in this environment");
        }

        String workspaceKey;
        try {
            workspaceKey = getWorkspaceEncryptionKeyForEnv(environment);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve workspace encryption key", e);
            throw new RuntimeException("Failed to retrieve workspace encryption key. Ensure it is set for this workspace.", e);
        }

        int version = 1;
        String aad = EncryptionService.aadFor(
                environment.getWorkspace().getId(),
                environment.getId(),
                createDto.getKey(),
                version);
        String encryptedValue;
        try {
            encryptedValue = encryptionService.encryptWithKey(createDto.getValue(), workspaceKey, aad);
        } catch (EncryptionException e) {
            log.error("Failed to encrypt secret with workspace key", e);
            throw new RuntimeException("Failed to encrypt secret. Check workspace encryption key.", e);
        }

        Secret secret = Secret.builder()
                .key(createDto.getKey())
                .value(encryptedValue)
                .environment(environment)
                .description(createDto.getDescription())
                .encrypted(true)
                .version(version)
                .currentVersion(version)
                .contentFingerprint(EncryptionService.fingerprint(createDto.getValue()))
                .secretType(createDto.getSecretType() != null ? createDto.getSecretType() : Secret.SecretType.TEXT)
                .tags(createDto.getTags())
                .build();

        Secret savedSecret = secretRepository.save(secret);
        recordVersion(savedSecret, version, currentUser.getEmail(), "Initial version");
        return mapToResponseDto(savedSecret, createDto.getValue());
    }

    @Transactional
    public List<SecretResponseDto> getAllSecretsForCurrentUser() {
        User currentUser = securityService.getCurrentUser();

        return secretRepository.findByEnvironmentWorkspaceOwnerOrMemberId(currentUser.getId()).stream()
                .map(secret -> {
                    try {
                        validateEnvironmentOwnership(secret.getEnvironment(), currentUser);
                    } catch (RuntimeException e) {
                        return null;
                    }
                    return decryptAndMap(secret);
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SecretResponseDto> getSecretsByEnvironment(Long environmentId) {
        User currentUser = securityService.getCurrentUser();
        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + environmentId));
        validateEnvironmentOwnership(environment, currentUser);

        return secretRepository.findByEnvironmentId(environmentId).stream()
                .map(this::decryptAndMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public SecretResponseDto getSecretByKey(String key) {
        User currentUser = securityService.getCurrentUser();
        List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId(key, currentUser.getId());
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("Secret not found with key: " + key);
        }
        Secret secret = secrets.get(0);
        validateEnvironmentOwnership(secret.getEnvironment(), currentUser);
        return decryptAndMap(secret);
    }

    @Transactional(readOnly = true)
    public SecretResponseDto getSecretByEnvironmentAndKey(Long environmentId, String key) {
        User currentUser = securityService.getCurrentUser();
        Secret secret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key + " in environment: " + environmentId));
        validateEnvironmentOwnership(secret.getEnvironment(), currentUser);
        return decryptAndMap(secret);
    }

    @Transactional
    public SecretResponseDto updateSecret(Long environmentId, String key, SecretUpdateDto updateDto) {
        User currentUser = securityService.getCurrentUser();
        Secret existingSecret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        validateEnvironmentOwnership(existingSecret.getEnvironment(), currentUser);
        requireWriteAccess(existingSecret.getEnvironment());

        String workspaceKey;
        try {
            workspaceKey = getWorkspaceEncryptionKeyForEnv(existingSecret.getEnvironment());
        } catch (Exception e) {
            log.error("Failed to retrieve workspace encryption key on update", e);
            throw new RuntimeException("Failed to retrieve workspace encryption key", e);
        }

        String newValue = updateDto.getValue();
        int nextVersion = (existingSecret.getVersion() == null ? 1 : existingSecret.getVersion()) + 1;
        String aad = EncryptionService.aadFor(
                existingSecret.getEnvironment().getWorkspace().getId(),
                existingSecret.getEnvironment().getId(),
                existingSecret.getKey(),
                nextVersion);
        try {
            existingSecret.setValue(encryptionService.encryptWithKey(newValue, workspaceKey, aad));
            existingSecret.setEncryptedValue(null);
        } catch (EncryptionException e) {
            log.error("Failed to encrypt secret on update", e);
            throw new RuntimeException("Failed to encrypt secret on update", e);
        }

        if (updateDto.getDescription() != null) {
            existingSecret.setDescription(updateDto.getDescription());
        }
        existingSecret.setVersion(nextVersion);
        existingSecret.setCurrentVersion(nextVersion);
        existingSecret.setEncrypted(true);
        existingSecret.setContentFingerprint(EncryptionService.fingerprint(newValue));

        Secret savedSecret = secretRepository.save(existingSecret);
        recordVersion(savedSecret, nextVersion, currentUser.getEmail(), "Secret updated");
        return mapToResponseDto(savedSecret, newValue);
    }

    @Transactional
    public void deleteSecret(Long environmentId, String key) {
        User currentUser = securityService.getCurrentUser();
        Secret secret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        validateEnvironmentOwnership(secret.getEnvironment(), currentUser);
        requireWriteAccess(secret.getEnvironment());
        secretRepository.delete(secret);
    }

    @Transactional
    public void deleteSecretGlobal(String key) {
        User currentUser = securityService.getCurrentUser();
        List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId(key, currentUser.getId());
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("Secret not found with key: " + key);
        }
        Secret secret = secrets.get(0);
        validateEnvironmentOwnership(secret.getEnvironment(), currentUser);
        requireWriteAccess(secret.getEnvironment());
        secretRepository.delete(secret);
    }

    private Secret getSecretForLifecycle(String key, String email) {
        User currentUser = securityService.getCurrentUser();
        Secret secret;
        if (currentUser == null) {
            if (!"system".equals(email)) {
                throw new RuntimeException("Unauthorized");
            }
            secret = secretRepository.findFirstByKeyOrderByIdAsc(key)
                    .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        } else {
            if (!currentUser.getEmail().equals(email)) {
                throw new RuntimeException("Unauthorized");
            }
            List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId(key, currentUser.getId());
            if (secrets.isEmpty()) {
                throw new ResourceNotFoundException("Secret not found with key: " + key);
            }
            secret = secrets.get(0);
        }
        requireWriteAccess(secret.getEnvironment());
        return secret;
    }

    @Transactional
    public void softDeleteSecret(String key, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        secret.setIsDeleted(true);
        secret.setUpdatedByEmail(email);
        secretRepository.save(secret);
    }

    @Transactional
    public void restoreSecret(String key, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        secret.setIsDeleted(false);
        secret.setUpdatedByEmail(email);
        secretRepository.save(secret);
    }

    @Transactional
    public void activateSecret(String key, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        secret.setIsActive(true);
        secret.setUpdatedByEmail(email);
        secretRepository.save(secret);
    }

    @Transactional
    public void deactivateSecret(String key, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        secret.setIsActive(false);
        secret.setUpdatedByEmail(email);
        secretRepository.save(secret);
    }

    @Transactional
    public Secret setRotationInterval(String key, Integer intervalDays, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        secret.setRotationIntervalDays(intervalDays);
        if (Boolean.TRUE.equals(secret.getAutoRotate()) && intervalDays != null) {
             secret.setNextRotationAt(java.time.LocalDateTime.now().plusDays(intervalDays));
        }
        secret.setUpdatedByEmail(email);
        return secretRepository.save(secret);
    }

    @Transactional
    public Secret enableAutoRotation(String key, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        if (secret.getRotationIntervalDays() == null) {
            throw new RuntimeException("Rotation interval must be set before enabling auto-rotation");
        }
        secret.setAutoRotate(true);
        secret.setNextRotationAt(java.time.LocalDateTime.now().plusDays(secret.getRotationIntervalDays()));
        secret.setUpdatedByEmail(email);
        return secretRepository.save(secret);
    }

    @Transactional
    public Secret disableAutoRotation(String key, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        secret.setAutoRotate(false);
        secret.setNextRotationAt(null);
        secret.setUpdatedByEmail(email);
        return secretRepository.save(secret);
    }

    @Transactional
    public Secret setExpiration(String key, java.time.LocalDateTime expiresAt, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        secret.setExpiresAt(expiresAt);
        secret.setUpdatedByEmail(email);
        return secretRepository.save(secret);
    }

    @Transactional
    public void removeExpiration(String key, String email) {
        Secret secret = getSecretForLifecycle(key, email);
        secret.setExpiresAt(null);
        secret.setUpdatedByEmail(email);
        secretRepository.save(secret);
    }

    @Transactional(readOnly = true)
    public List<Secret> getSecretsNeedingRotation() {
        return secretRepository.findByAutoRotateTrueAndNextRotationAtLessThanEqual(java.time.LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Secret> getExpiredSecrets() {
        return secretRepository.findByIsActiveTrueAndExpiresAtLessThanEqual(java.time.LocalDateTime.now());
    }

    @Transactional
    public void cleanupSoftDeletedSecrets(int retentionDays) {
        java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusDays(retentionDays);
        List<Secret> secretsToDelete = secretRepository.findByIsDeletedTrueAndUpdatedAtLessThanEqual(cutoffDate);
        secretRepository.deleteAll(secretsToDelete);
    }

    private SecretResponseDto decryptAndMap(Secret secret) {
        String plainValue;
        String storedValue = secret.getValue() != null ? secret.getValue() : secret.getEncryptedValue();

        if (!Boolean.TRUE.equals(secret.getEncrypted())) {
            plainValue = storedValue;
        } else {
            try {
                String workspaceKey = getWorkspaceEncryptionKeyForEnv(secret.getEnvironment());
                String aad = EncryptionService.aadFor(
                        secret.getEnvironment().getWorkspace().getId(),
                        secret.getEnvironment().getId(),
                        secret.getKey(),
                        secret.getVersion() != null ? secret.getVersion() : 1);
                try {
                    plainValue = encryptionService.decryptWithKey(storedValue, workspaceKey, aad);
                } catch (EncryptionException aadMiss) {
                    // Legacy ciphertext written without AAD
                    plainValue = encryptionService.decryptWithKey(storedValue, workspaceKey);
                }
            } catch (ResourceNotFoundException e) {
                log.warn("Workspace key missing for secret: {} - {}", secret.getKey(), e.getMessage());
                plainValue = "[WORKSPACE_KEY_NOT_SET]";
            } catch (EncryptionException e) {
                log.error("Failed to decrypt secret: {} - wrong workspace key, AAD, or corrupted", secret.getKey(), e);
                plainValue = "[DECRYPTION_ERROR: Invalid workspace key]";
            } catch (Exception e) {
                log.error("Unexpected error decrypting secret: {}", secret.getKey(), e);
                plainValue = "[DECRYPTION_ERROR]";
            }
        }
        return mapToResponseDto(secret, plainValue);
    }

    private SecretResponseDto mapToResponseDto(Secret secret, String plainValue) {
        return SecretResponseDto.builder()
                .id(secret.getId())
                .key(secret.getKey())
                .value(plainValue)
                .environmentId(secret.getEnvironment() != null ? secret.getEnvironment().getId() : null)
                .description(secret.getDescription())
                .encrypted(true)
                .version(secret.getVersion())
                .createdAt(secret.getCreatedAt())
                .updatedAt(secret.getUpdatedAt())
                .secretType(secret.getSecretType())
                .tags(secret.getTags())
                .isActive(secret.getIsActive())
                .isDeleted(secret.getIsDeleted())
                .autoRotate(secret.getAutoRotate())
                .rotationIntervalDays(secret.getRotationIntervalDays())
                .lastRotatedAt(secret.getLastRotatedAt())
                .nextRotationAt(secret.getNextRotationAt())
                .expiresAt(secret.getExpiresAt())
                .build();
    }

    @Transactional
    public SecretResponseDto rotateSecret(Long environmentId, String key, String newValue) {
        Secret secret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        User currentUser = securityService.getCurrentUser();
        if (currentUser != null) {
            validateEnvironmentOwnership(secret.getEnvironment(), currentUser);
        }
        requireWriteAccess(secret.getEnvironment());
        String actorEmail = currentUser != null ? currentUser.getEmail() : "system";

        updateSecret(environmentId, key, SecretUpdateDto.builder()
                .value(newValue)
                .description(secret.getDescription())
                .build());
        Secret updated = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        updated.setLastRotatedAt(java.time.LocalDateTime.now());
        if (updated.getRotationIntervalDays() != null && updated.getRotationIntervalDays() > 0) {
            updated.setNextRotationAt(java.time.LocalDateTime.now().plusDays(updated.getRotationIntervalDays()));
        }
        updated.setUpdatedByEmail(actorEmail);
        Secret saved = secretRepository.save(updated);
        return mapToResponseDto(saved, newValue);
    }

    private void recordVersion(Secret secret, int versionNumber, String actorEmail, String reason) {
        if (secret.getId() == null || secret.getValue() == null) {
            return;
        }
        secretVersionRepository.deactivateAllForSecret(secret.getId());
        secretVersionRepository.save(SecretVersion.builder()
                .secretKey(secret.getKey())
                .secret(secret)
                .environment(secret.getEnvironment())
                .workspace(secret.getEnvironment().getWorkspace())
                .versionNumber(versionNumber)
                .encryptedValue(secret.getValue())
                .isActive(true)
                .rotationReason(reason)
                .rotatedByEmail(actorEmail != null ? actorEmail : "system")
                .contentFingerprint(secret.getContentFingerprint())
                .build());
    }

    /**
     * Get the decrypted value of a secret for reveal operations.
     * This method is used with step-up MFA protection.
     */
    @Transactional(readOnly = true)
    public String getDecryptedSecretValue(Long environmentId, String key) {
        User currentUser = securityService.getCurrentUser();
        Secret secret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key + " in environment: " + environmentId));
        validateEnvironmentOwnership(secret.getEnvironment(), currentUser);

        String storedValue = secret.getValue() != null ? secret.getValue() : secret.getEncryptedValue();

        if (!Boolean.TRUE.equals(secret.getEncrypted())) {
            return storedValue;
        }

        try {
            String workspaceKey = getWorkspaceEncryptionKeyForEnv(secret.getEnvironment());
            String aad = EncryptionService.aadFor(
                    secret.getEnvironment().getWorkspace().getId(),
                    secret.getEnvironment().getId(),
                    secret.getKey(),
                    secret.getVersion() != null ? secret.getVersion() : 1);
            try {
                return encryptionService.decryptWithKey(storedValue, workspaceKey, aad);
            } catch (EncryptionException aadMiss) {
                // Legacy ciphertext written without AAD
                return encryptionService.decryptWithKey(storedValue, workspaceKey);
            }
        } catch (ResourceNotFoundException e) {
            log.warn("Workspace key missing for secret: {} - {}", secret.getKey(), e.getMessage());
            throw new RuntimeException("Workspace encryption key not set");
        } catch (EncryptionException e) {
            log.error("Failed to decrypt secret: {} - wrong workspace key, AAD, or corrupted", secret.getKey(), e);
            throw new RuntimeException("Failed to decrypt secret - invalid workspace key or corrupted data");
        } catch (Exception e) {
            log.error("Unexpected error decrypting secret: {}", secret.getKey(), e);
            throw new RuntimeException("Decryption error");
        }
    }
}

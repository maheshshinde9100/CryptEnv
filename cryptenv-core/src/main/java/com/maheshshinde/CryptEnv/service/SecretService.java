package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.SecretCreateDto;
import com.maheshshinde.CryptEnv.dto.SecretResponseDto;
import com.maheshshinde.CryptEnv.dto.SecretUpdateDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
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
    private final EnvironmentRepository environmentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EncryptionService encryptionService;
    private final SecurityService securityService;
    private final WorkspaceService workspaceService;

    private void validateEnvironmentOwnership(Environment environment, User user) {
        Long ownerId = environment.getWorkspace().getOwner().getId();
        boolean isOwner = ownerId.equals(user.getId());
        boolean isMember = environment.getWorkspace().getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        if (!isOwner && !isMember) {
            throw new RuntimeException("Access denied: You do not have permission to access secrets in this workspace");
        }
    }

    private String getWorkspaceEncryptionKeyForEnv(Environment environment) {
        Long workspaceId = environment.getWorkspace().getId();
        return workspaceService.getDecryptedWorkspaceKey(workspaceId);
    }

    private Environment getOrCreateDefaultEnvironment(User user) {
        List<Workspace> ownedWorkspaces = workspaceRepository.findByOwnerId(user.getId());
        List<Workspace> memberWorkspaces = workspaceRepository.findByMembersId(user.getId());
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

        String encryptedValue;
        try {
            encryptedValue = encryptionService.encryptWithKey(createDto.getValue(), workspaceKey);
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
                .encryptedValue(encryptedValue)
                .build();

        Secret savedSecret = secretRepository.save(secret);
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

        String workspaceKey;
        try {
            workspaceKey = getWorkspaceEncryptionKeyForEnv(existingSecret.getEnvironment());
        } catch (Exception e) {
            log.error("Failed to retrieve workspace encryption key on update", e);
            throw new RuntimeException("Failed to retrieve workspace encryption key", e);
        }

        String newValue = updateDto.getValue();
        try {
            existingSecret.setValue(encryptionService.encryptWithKey(newValue, workspaceKey));
            existingSecret.setEncryptedValue(existingSecret.getValue());
        } catch (EncryptionException e) {
            log.error("Failed to encrypt secret on update", e);
            throw new RuntimeException("Failed to encrypt secret on update", e);
        }

        if (updateDto.getDescription() != null) {
            existingSecret.setDescription(updateDto.getDescription());
        }
        existingSecret.setVersion(existingSecret.getVersion() + 1);
        existingSecret.setEncrypted(true);

        Secret savedSecret = secretRepository.save(existingSecret);
        return mapToResponseDto(savedSecret, newValue);
    }

    @Transactional
    public void deleteSecret(Long environmentId, String key) {
        User currentUser = securityService.getCurrentUser();
        Secret secret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        validateEnvironmentOwnership(secret.getEnvironment(), currentUser);
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
        secretRepository.delete(secret);
    }

    private Secret getSecretForLifecycle(String key, String email) {
        User currentUser = securityService.getCurrentUser();
        if (!currentUser.getEmail().equals(email)) {
             throw new RuntimeException("Unauthorized");
        }
        List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId(key, currentUser.getId());
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("Secret not found with key: " + key);
        }
        Secret secret = secrets.get(0);
        validateEnvironmentOwnership(secret.getEnvironment(), currentUser);
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
        String storedValue = secret.getEncryptedValue() != null ? secret.getEncryptedValue() : secret.getValue();

        if (!Boolean.TRUE.equals(secret.getEncrypted())) {
            plainValue = storedValue;
        } else {
            try {
                String workspaceKey = getWorkspaceEncryptionKeyForEnv(secret.getEnvironment());
                plainValue = encryptionService.decryptWithKey(storedValue, workspaceKey);
            } catch (ResourceNotFoundException e) {
                log.warn("Workspace key missing for secret: {} - {}", secret.getKey(), e.getMessage());
                plainValue = "[WORKSPACE_KEY_NOT_SET]";
            } catch (EncryptionException e) {
                log.error("Failed to decrypt secret: {} - wrong workspace key or corrupted", secret.getKey(), e);
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
                .build();
    }
}

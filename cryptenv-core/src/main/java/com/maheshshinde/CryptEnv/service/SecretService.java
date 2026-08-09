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

    private Environment getOrCreateDefaultEnvironment(User user) {
        List<Workspace> workspaces = workspaceRepository.findByOwnerId(user.getId());
        Workspace finalWorkspace;
        if (workspaces.isEmpty()) {
            Workspace newWorkspace = Workspace.builder()
                    .name(user.getUsername() + "-workspace")
                    .description("Default workspace for " + user.getUsername())
                    .owner(user)
                    .build();
            finalWorkspace = workspaceRepository.save(newWorkspace);
        } else {
            finalWorkspace = workspaces.get(0);
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
        }

        // Check key uniqueness within this environment
        if (secretRepository.existsByEnvironmentIdAndKey(environment.getId(), createDto.getKey())) {
            throw new ResourceAlreadyExistsException("Secret with key '" + createDto.getKey() + "' already exists in this environment");
        }

        String valueToStore = createDto.getValue();
        boolean shouldEncrypt = Boolean.TRUE.equals(createDto.getEncrypted());

        try {
            if (shouldEncrypt) {
                valueToStore = encryptionService.encrypt(createDto.getValue());
            }
        } catch (Exception e) {
            log.error("Failed to encrypt secret", e);
            throw new RuntimeException("Failed to encrypt secret", e);
        }

        Secret secret = Secret.builder()
                .key(createDto.getKey())
                .value(valueToStore)
                .environment(environment)
                .description(createDto.getDescription())
                .encrypted(shouldEncrypt)
                .build();

        Secret savedSecret = secretRepository.save(secret);
        return mapToResponseDto(savedSecret, createDto.getValue());
    }

    @Transactional
    public List<SecretResponseDto> getAllSecretsForCurrentUser() {
        User currentUser = securityService.getCurrentUser();
        
        // Ensure default environment exists so user starts with a workspace & env
        getOrCreateDefaultEnvironment(currentUser);

        return secretRepository.findByEnvironmentWorkspaceOwnerId(currentUser.getId()).stream()
                .map(this::decryptAndMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SecretResponseDto> getSecretsByEnvironment(Long environmentId) {
        return secretRepository.findByEnvironmentId(environmentId).stream()
                .map(this::decryptAndMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public SecretResponseDto getSecretByKey(String key) {
        User currentUser = securityService.getCurrentUser();
        List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerId(key, currentUser.getId());
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("Secret not found with key: " + key);
        }
        return decryptAndMap(secrets.get(0));
    }

    @Transactional(readOnly = true)
    public SecretResponseDto getSecretByEnvironmentAndKey(Long environmentId, String key) {
        Secret secret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key + " in environment: " + environmentId));
        return decryptAndMap(secret);
    }

    @Transactional
    public SecretResponseDto updateSecret(Long environmentId, String key, SecretUpdateDto updateDto) {
        Secret existingSecret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));

        String newValue = updateDto.getValue();
        try {
            if (Boolean.TRUE.equals(existingSecret.getEncrypted())) {
                existingSecret.setValue(encryptionService.encrypt(newValue));
            } else {
                existingSecret.setValue(newValue);
            }
        } catch (Exception e) {
            log.error("Failed to encrypt secret on update", e);
            throw new RuntimeException("Failed to encrypt secret on update", e);
        }

        if (updateDto.getDescription() != null) {
            existingSecret.setDescription(updateDto.getDescription());
        }
        existingSecret.setVersion(existingSecret.getVersion() + 1);

        Secret savedSecret = secretRepository.save(existingSecret);
        return mapToResponseDto(savedSecret, newValue);
    }

    @Transactional
    public void deleteSecret(Long environmentId, String key) {
        Secret secret = secretRepository.findByEnvironmentIdAndKey(environmentId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        secretRepository.delete(secret);
    }

    @Transactional
    public void deleteSecretGlobal(String key) {
        User currentUser = securityService.getCurrentUser();
        List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerId(key, currentUser.getId());
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("Secret not found with key: " + key);
        }
        secretRepository.delete(secrets.get(0));
    }

    private Secret getSecretForLifecycle(String key, String email) {
        // Find secret by key and owner's email (using securityService to ensure we only touch our own)
        User currentUser = securityService.getCurrentUser();
        if (!currentUser.getEmail().equals(email)) {
             throw new RuntimeException("Unauthorized");
        }
        List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerId(key, currentUser.getId());
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("Secret not found with key: " + key);
        }
        return secrets.get(0);
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
        String decryptedValue = secret.getValue();
        if (Boolean.TRUE.equals(secret.getEncrypted()) && decryptedValue != null) {
            try {
                decryptedValue = encryptionService.decrypt(decryptedValue);
            } catch (Exception e) {
                log.error("Failed to decrypt secret: {}", secret.getKey(), e);
                decryptedValue = "[DECRYPTION_ERROR]";
            }
        }
        return mapToResponseDto(secret, decryptedValue);
    }

    private SecretResponseDto mapToResponseDto(Secret secret, String plainValue) {
        return SecretResponseDto.builder()
                .id(secret.getId())
                .key(secret.getKey())
                .value(plainValue)
                .environmentId(secret.getEnvironment() != null ? secret.getEnvironment().getId() : null)
                .description(secret.getDescription())
                .encrypted(secret.getEncrypted())
                .version(secret.getVersion())
                .createdAt(secret.getCreatedAt())
                .updatedAt(secret.getUpdatedAt())
                .build();
    }
}

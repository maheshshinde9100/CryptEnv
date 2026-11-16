package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.model.SecretVersion;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import com.maheshshinde.CryptEnv.repository.SecretVersionRepository;
import com.maheshshinde.CryptEnv.security.EncryptionException;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import com.maheshshinde.CryptEnv.security.EnvelopeEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretVersionService {

    private final SecretVersionRepository secretVersionRepository;
    private final SecretRepository secretRepository;
    private final EncryptionService encryptionService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final SecurityService securityService;
    private final WorkspaceService workspaceService;

    private void validateSecretAccess(Secret secret, User user) {
        Workspace workspace = secret.getEnvironment().getWorkspace();
        boolean isOwner = workspace.getOwner().getId().equals(user.getId());
        boolean isMember = workspace.getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()));
        if (!isOwner && !isMember) {
            throw new RuntimeException("Access denied: You do not have permission to access versions for this secret");
        }
    }

    private Secret findSecretWithAccessCheck(String secretKey) {
        User currentUser = securityService.getCurrentUser();
        List<Secret> secrets = secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId(secretKey, currentUser.getId());
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("Secret not found with key: " + secretKey);
        }
        Secret secret = secrets.get(0);
        validateSecretAccess(secret, currentUser);
        return secret;
    }

    @Transactional
    public SecretVersion createVersion(String secretKey, String value, String rotatedByEmail, String reason) {
        User currentUser = securityService.getCurrentUser();
        if (!currentUser.getEmail().equals(rotatedByEmail)) {
            throw new RuntimeException("Unauthorized: rotatedByEmail does not match current user");
        }

        Secret secret = findSecretWithAccessCheck(secretKey);
        Environment environment = secret.getEnvironment();
        Workspace workspace = environment.getWorkspace();

        String workspaceKey;
        try {
            workspaceKey = workspaceService.getDecryptedWorkspaceKey(workspace.getId());
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve workspace encryption key for version creation", e);
            throw new RuntimeException("Failed to retrieve workspace encryption key", e);
        }

        String encryptedValue;
        String wrappedDek = null;
        String dekAlgorithm = null;
        String dekIv = null;
        String dekTag = null;

        try {
            if (envelopeEncryptionService.isEnabled()) {
                // Use envelope encryption
                EnvelopeEncryptionService.EnvelopeResult result = envelopeEncryptionService.encryptWithEnvelope(value, workspaceKey);
                encryptedValue = result.getCiphertext();
                if (result.isEnvelopeMode()) {
                    wrappedDek = result.getWrappedDek();
                    dekAlgorithm = result.getDekAlgorithm();
                    dekIv = result.getDekIv();
                    dekTag = result.getDekTag();
                }
            } else {
                // Legacy direct encryption
                encryptedValue = encryptionService.encryptWithKey(value, workspaceKey);
            }
        } catch (EncryptionException e) {
            log.error("Failed to encrypt secret value for version", e);
            throw new RuntimeException("Failed to encrypt secret value for version", e);
        }

        secretVersionRepository.findBySecretKeyAndIsActiveTrue(secretKey)
                .ifPresent(version -> {
                    version.setIsActive(false);
                    secretVersionRepository.save(version);
                });

        Integer nextVersion = secret.getCurrentVersion() + 1;
        secret.setCurrentVersion(nextVersion);
        secretRepository.save(secret);

        SecretVersion newVersion = SecretVersion.builder()
                .secretKey(secretKey)
                .secret(secret)
                .environment(environment)
                .workspace(workspace)
                .versionNumber(nextVersion)
                .encryptedValue(encryptedValue)
                .wrappedDek(wrappedDek)
                .dekAlgorithm(dekAlgorithm)
                .dekIv(dekIv)
                .dekTag(dekTag)
                .isActive(true)
                .rotationReason(reason)
                .rotatedByEmail(rotatedByEmail)
                .contentFingerprint(EncryptionService.fingerprint(value))
                .createdAt(LocalDateTime.now())
                .build();

        return secretVersionRepository.save(newVersion);
    }

    @Transactional(readOnly = true)
    public List<SecretVersion> getSecretHistory(String secretKey) {
        findSecretWithAccessCheck(secretKey);
        return secretVersionRepository.findBySecretKeyOrderByVersionNumberDesc(secretKey);
    }

    @Transactional(readOnly = true)
    public Optional<SecretVersion> getVersion(String secretKey, Integer versionNumber) {
        findSecretWithAccessCheck(secretKey);
        return secretVersionRepository.findBySecretKeyAndVersionNumber(secretKey, versionNumber);
    }

    @Transactional(readOnly = true)
    public Optional<SecretVersion> getActiveVersion(String secretKey) {
        findSecretWithAccessCheck(secretKey);
        return secretVersionRepository.findBySecretKeyAndIsActiveTrue(secretKey);
    }

    @Transactional
    public SecretVersion rollbackToVersion(String secretKey, Integer versionNumber, String rolledBackByEmail) {
        User currentUser = securityService.getCurrentUser();
        if (!currentUser.getEmail().equals(rolledBackByEmail)) {
            throw new RuntimeException("Unauthorized: rolledBackByEmail does not match current user");
        }

        Secret secret = findSecretWithAccessCheck(secretKey);

        SecretVersion targetVersion = secretVersionRepository.findBySecretKeyAndVersionNumber(secretKey, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found"));

        secretVersionRepository.findBySecretKeyAndIsActiveTrue(secretKey)
                .ifPresent(version -> {
                    version.setIsActive(false);
                    secretVersionRepository.save(version);
                });

        targetVersion.setIsActive(true);
        targetVersion.setRotationReason("Rollback to version " + versionNumber);
        targetVersion.setRotatedByEmail(rolledBackByEmail);
        secretVersionRepository.save(targetVersion);

        secret.setValue(targetVersion.getEncryptedValue());
        secret.setEncryptedValue(null);
        secret.setUpdatedByEmail(rolledBackByEmail);
        secretRepository.save(secret);

        return targetVersion;
    }

    @Transactional
    public void deleteVersionsOlderThan(LocalDateTime beforeDate) {
        List<SecretVersion> oldVersions = secretVersionRepository.findVersionsOlderThan(beforeDate);
        secretVersionRepository.deleteAll(oldVersions);
    }

    @Transactional
    public void deleteAllVersions(String secretKey) {
        findSecretWithAccessCheck(secretKey);
        secretVersionRepository.deleteBySecretKey(secretKey);
    }

    /**
     * Decrypt a secret version value, handling both envelope and legacy encryption.
     */
    @Transactional(readOnly = true)
    public String decryptVersionValue(SecretVersion version, String workspaceKey) {
        try {
            if (version.getWrappedDek() != null && !version.getWrappedDek().isBlank()) {
                // Envelope encryption mode
                return envelopeEncryptionService.decryptWithEnvelope(
                        version.getEncryptedValue(),
                        workspaceKey,
                        version.getWrappedDek(),
                        version.getDekAlgorithm(),
                        version.getDekIv(),
                        version.getDekTag()
                );
            } else {
                // Legacy direct encryption mode
                return encryptionService.decryptWithKey(version.getEncryptedValue(), workspaceKey);
            }
        } catch (EncryptionException e) {
            log.error("Failed to decrypt secret version {}", version.getId(), e);
            throw new RuntimeException("Failed to decrypt secret value", e);
        }
    }
}

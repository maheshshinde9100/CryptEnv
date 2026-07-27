package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.model.SecretVersion;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import com.maheshshinde.CryptEnv.repository.SecretVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SecretVersionService {

    private final SecretVersionRepository secretVersionRepository;
    private final SecretRepository secretRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public SecretVersion createVersion(String secretKey, String value, String rotatedByEmail, String reason) {
        Secret secret = secretRepository.findByKey(secretKey)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        // Deactivate current active version
        secretVersionRepository.findBySecretKeyAndIsActiveTrue(secretKey)
                .ifPresent(version -> {
                    version.setIsActive(false);
                    secretVersionRepository.save(version);
                });

        // Increment version number
        Integer nextVersion = secret.getCurrentVersion() + 1;
        secret.setCurrentVersion(nextVersion);
        secretRepository.save(secret);

        // Create new version
        SecretVersion newVersion = SecretVersion.builder()
                .secretKey(secretKey)
                .versionNumber(nextVersion)
                .encryptedValue(encryptionService.encrypt(value))
                .isActive(true)
                .rotationReason(reason)
                .rotatedByEmail(rotatedByEmail)
                .createdAt(LocalDateTime.now())
                .build();

        return secretVersionRepository.save(newVersion);
    }

    @Transactional(readOnly = true)
    public List<SecretVersion> getSecretHistory(String secretKey) {
        return secretVersionRepository.findBySecretKeyOrderByVersionNumberDesc(secretKey);
    }

    @Transactional(readOnly = true)
    public Optional<SecretVersion> getVersion(String secretKey, Integer versionNumber) {
        return secretVersionRepository.findBySecretKeyAndVersionNumber(secretKey, versionNumber);
    }

    @Transactional(readOnly = true)
    public Optional<SecretVersion> getActiveVersion(String secretKey) {
        return secretVersionRepository.findBySecretKeyAndIsActiveTrue(secretKey);
    }

    @Transactional
    public SecretVersion rollbackToVersion(String secretKey, Integer versionNumber, String rolledBackByEmail) {
        Secret secret = secretRepository.findByKey(secretKey)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        SecretVersion targetVersion = secretVersionRepository.findBySecretKeyAndVersionNumber(secretKey, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found"));

        // Deactivate current version
        secretVersionRepository.findBySecretKeyAndIsActiveTrue(secretKey)
                .ifPresent(version -> {
                    version.setIsActive(false);
                    secretVersionRepository.save(version);
                });

        // Activate target version
        targetVersion.setIsActive(true);
        targetVersion.setRotationReason("Rollback to version " + versionNumber);
        targetVersion.setRotatedByEmail(rolledBackByEmail);
        secretVersionRepository.save(targetVersion);

        // Update secret with rolled back value
        secret.setEncryptedValue(targetVersion.getEncryptedValue());
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
        secretVersionRepository.deleteBySecretKey(secretKey);
    }
}

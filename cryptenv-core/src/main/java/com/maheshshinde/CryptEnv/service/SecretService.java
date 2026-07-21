package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SecretService {

    private final SecretRepository secretRepository;
    private final EncryptionService encryptionService;
    private final SecretVersionService secretVersionService;

    @Transactional
    public Secret createSecret(Secret secret, String createdByEmail) {
        secret.setCreatedByEmail(createdByEmail);
        secret.setCurrentVersion(1);
        secret.setIsActive(true);
        secret.setIsDeleted(false);
        secret.setCreatedAt(LocalDateTime.now());
        
        Secret savedSecret = secretRepository.save(secret);
        
        // Create initial version
        secretVersionService.createVersion(secret.getKey(), 
                encryptionService.decrypt(secret.getEncryptedValue()), 
                createdByEmail, 
                "Initial version");
        
        return savedSecret;
    }

    @Transactional(readOnly = true)
    public List<Secret> getAllSecrets() {
        return secretRepository.findByIsDeletedFalse();
    }

    @Transactional(readOnly = true)
    public Page<Secret> getAllSecrets(Pageable pageable) {
        return secretRepository.findByIsDeletedFalse(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Secret> getSecretByKey(String key) {
        return secretRepository.findByKey(key)
                .filter(secret -> !secret.getIsDeleted());
    }

    @Transactional
    public Secret updateSecret(String key, Secret updatedSecret, String updatedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        if (secret.getIsDeleted()) {
            throw new RuntimeException("Cannot update deleted secret");
        }

        // Create new version if value changed
        if (!secret.getEncryptedValue().equals(updatedSecret.getEncryptedValue())) {
            secretVersionService.createVersion(key, 
                    encryptionService.decrypt(updatedSecret.getEncryptedValue()), 
                    updatedByEmail, 
                    "Manual update");
        }

        secret.setEncryptedValue(updatedSecret.getEncryptedValue());
        secret.setDescription(updatedSecret.getDescription());
        secret.setUpdatedByEmail(updatedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        return secretRepository.save(secret);
    }

    @Transactional
    public void softDeleteSecret(String key, String deletedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        secret.setIsDeleted(true);
        secret.setIsActive(false);
        secret.setDeletedAt(LocalDateTime.now());
        secret.setUpdatedByEmail(deletedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        secretRepository.save(secret);
    }

    @Transactional
    public void restoreSecret(String key, String restoredByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        if (!secret.getIsDeleted()) {
            throw new RuntimeException("Secret is not deleted");
        }

        secret.setIsDeleted(false);
        secret.setIsActive(true);
        secret.setDeletedAt(null);
        secret.setUpdatedByEmail(restoredByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        secretRepository.save(secret);
    }

    @Transactional
    public void hardDeleteSecret(String key) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        secretVersionService.deleteAllVersions(key);
        secretRepository.delete(secret);
    }

    @Transactional
    public void activateSecret(String key, String activatedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        if (secret.getIsDeleted()) {
            throw new RuntimeException("Cannot activate deleted secret");
        }

        secret.setIsActive(true);
        secret.setUpdatedByEmail(activatedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        secretRepository.save(secret);
    }

    @Transactional
    public void deactivateSecret(String key, String deactivatedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        secret.setIsActive(false);
        secret.setUpdatedByEmail(deactivatedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        secretRepository.save(secret);
    }

    @Transactional
    public Secret setRotationInterval(String key, Integer intervalDays, String updatedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        secret.setRotationIntervalDays(intervalDays);
        secret.setUpdatedByEmail(updatedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        if (intervalDays != null && intervalDays > 0) {
            LocalDateTime nextRotation = LocalDateTime.now().plusDays(intervalDays);
            secret.setNextRotationAt(nextRotation);
        } else {
            secret.setNextRotationAt(null);
        }

        return secretRepository.save(secret);
    }

    @Transactional
    public Secret enableAutoRotation(String key, String updatedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        if (secret.getRotationIntervalDays() == null || secret.getRotationIntervalDays() <= 0) {
            throw new RuntimeException("Rotation interval must be set before enabling auto-rotation");
        }

        secret.setAutoRotate(true);
        secret.setUpdatedByEmail(updatedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        return secretRepository.save(secret);
    }

    @Transactional
    public Secret disableAutoRotation(String key, String updatedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        secret.setAutoRotate(false);
        secret.setNextRotationAt(null);
        secret.setUpdatedByEmail(updatedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        return secretRepository.save(secret);
    }

    @Transactional
    public Secret setExpiration(String key, LocalDateTime expiresAt, String updatedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        secret.setExpiresAt(expiresAt);
        secret.setUpdatedByEmail(updatedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        return secretRepository.save(secret);
    }

    @Transactional
    public void removeExpiration(String key, String updatedByEmail) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Secret not found"));

        secret.setExpiresAt(null);
        secret.setUpdatedByEmail(updatedByEmail);
        secret.setUpdatedAt(LocalDateTime.now());

        secretRepository.save(secret);
    }

    @Transactional(readOnly = true)
    public List<Secret> getSecretsNeedingRotation() {
        return secretRepository.findSecretsNeedingRotation(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Secret> getExpiredSecrets() {
        return secretRepository.findExpiredSecrets(LocalDateTime.now());
    }

    @Transactional
    public void cleanupSoftDeletedSecrets(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<Secret> oldDeletedSecrets = secretRepository.findSoftDeletedOlderThan(cutoffDate);
        
        for (Secret secret : oldDeletedSecrets) {
            secretVersionService.deleteAllVersions(secret.getKey());
            secretRepository.delete(secret);
        }
    }
}

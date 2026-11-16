package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.MasterKeyRotation;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.repository.MasterKeyRotationRepository;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MasterKeyRotationService {

    private final MasterKeyRotationRepository masterKeyRotationRepository;
    private final SecretRepository secretRepository;

    @Transactional
    public MasterKeyRotation startRotation(Long workspaceId, String oldKeyHash, String newKeyHash, Long createdBy) {
        MasterKeyRotation existing = masterKeyRotationRepository.findByWorkspaceIdAndStatus(workspaceId, "in_progress").orElse(null);
        if (existing != null) {
            throw new IllegalStateException("Rotation already in progress");
        }

        List<Secret> secrets = secretRepository.findByWorkspaceId(workspaceId);
        MasterKeyRotation rotation = MasterKeyRotation.builder()
                .workspaceId(workspaceId)
                .oldKeyHash(oldKeyHash)
                .newKeyHash(newKeyHash)
                .status("in_progress")
                .totalSecrets(secrets.size())
                .processedSecrets(0)
                .failedSecrets(0)
                .createdBy(createdBy)
                .build();
        
        rotation = masterKeyRotationRepository.save(rotation);
        performRotation(rotation.getId());
        return rotation;
    }

    @Async
    @Transactional
    public void performRotation(Long rotationId) {
        MasterKeyRotation rotation = masterKeyRotationRepository.findById(rotationId)
                .orElseThrow(() -> new IllegalArgumentException("Rotation not found"));

        try {
            List<Secret> secrets = secretRepository.findByWorkspaceId(rotation.getWorkspaceId());
            for (Secret secret : secrets) {
                try {
                    // TODO: Implement rewrap logic when envelope encryption is fully integrated
                    // For now, just mark as processed
                    rotation.setProcessedSecrets(rotation.getProcessedSecrets() + 1);
                } catch (Exception e) {
                    log.error("Failed to rotate secret {}", secret.getId(), e);
                    rotation.setFailedSecrets(rotation.getFailedSecrets() + 1);
                }
            }

            rotation.setStatus("completed");
            rotation.setCompletedAt(LocalDateTime.now());
            masterKeyRotationRepository.save(rotation);
        } catch (Exception e) {
            rotation.setStatus("failed");
            rotation.setErrorMessage(e.getMessage());
            masterKeyRotationRepository.save(rotation);
        }
    }

    public List<MasterKeyRotation> getWorkspaceRotations(Long workspaceId) {
        return masterKeyRotationRepository.findByWorkspaceId(workspaceId);
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

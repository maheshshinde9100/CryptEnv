package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Secret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled jobs for secret rotation, expiry, and soft-delete cleanup.
 * Rotation uses {@link SecretValueGenerator} (no placeholder junk values).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecretRotationScheduler {

    private final SecretService secretService;
    private final SecretVersionService secretVersionService;
    private final SecretValueGenerator secretValueGenerator;

    @Scheduled(cron = "${secret.rotation.cron:0 0 * * * ?}")
    public void rotateSecrets() {
        log.info("Starting scheduled secret rotation check");

        List<Secret> secretsNeedingRotation = secretService.getSecretsNeedingRotation();
        int rotated = 0;
        int failed = 0;

        for (Secret secret : secretsNeedingRotation) {
            try {
                rotateOne(secret);
                rotated++;
                log.info("Successfully rotated secret: {}", secret.getKey());
            } catch (Exception e) {
                failed++;
                log.error("Failed to rotate secret: {}", secret.getKey(), e);
            }
        }

        log.info("Completed scheduled secret rotation. candidates={} rotated={} failed={}",
                secretsNeedingRotation.size(), rotated, failed);
    }

    /**
     * Public entry so Spring proxy applies {@code @Transactional} (avoids self-invocation).
     */
    @Transactional
    public void rotateOne(Secret secret) {
        if (!Boolean.TRUE.equals(secret.getAutoRotate())) {
            log.warn("Secret {} is marked for rotation but auto-rotate is disabled", secret.getKey());
            return;
        }

        String newValue = secretValueGenerator.generate(secret);

        secretService.rotateSecret(secret.getEnvironment().getId(), secret.getKey(), newValue);

        log.info("Rotated secret {} with generated {} value", secret.getKey(), secretValueGenerator.resolveType(secret));
    }

    @Scheduled(cron = "${secret.expiration.check.cron:0 0 * * * ?}")
    @Transactional
    public void checkExpiredSecrets() {
        log.info("Starting expired secret check");

        List<Secret> expiredSecrets = secretService.getExpiredSecrets();

        for (Secret secret : expiredSecrets) {
            try {
                secretService.deactivateSecret(secret.getKey(), "system");
                log.warn("Deactivated expired secret: {}, expired at: {}", secret.getKey(), secret.getExpiresAt());
            } catch (Exception e) {
                log.error("Failed to handle expired secret: {}", secret.getKey(), e);
            }
        }

        log.info("Completed expired secret check. Handled {} secrets", expiredSecrets.size());
    }

    @Scheduled(cron = "${secret.cleanup.cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupOldSecrets() {
        log.info("Starting cleanup of old deleted secrets");

        int retentionDays = 90;
        try {
            secretService.cleanupSoftDeletedSecrets(retentionDays);
            log.info("Completed cleanup of soft-deleted secrets older than {} days", retentionDays);
        } catch (Exception e) {
            log.error("Failed to cleanup old secrets", e);
        }
    }
}

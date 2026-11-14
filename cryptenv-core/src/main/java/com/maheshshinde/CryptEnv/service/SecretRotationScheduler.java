package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Secret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled jobs for secret rotation, expiry, and soft-delete cleanup.
 * Auto-rotation is intentionally disabled until real secret generators exist (§5);
 * the previous placeholder ("rotated_" + timestamp) overwrote live secrets with junk.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecretRotationScheduler {

    private final SecretService secretService;

    @Scheduled(cron = "${secret.rotation.cron:0 0 * * * ?}")
    public void rotateSecrets() {
        log.info("Starting scheduled secret rotation check");

        List<Secret> secretsNeedingRotation = secretService.getSecretsNeedingRotation();
        int skipped = 0;

        for (Secret secret : secretsNeedingRotation) {
            if (Boolean.TRUE.equals(secret.getAutoRotate())) {
                // Do not overwrite with placeholder values. Re-enable in §5 with real generators.
                log.warn(
                        "Skipping auto-rotation for secret '{}' (id={}): generators not implemented yet. "
                                + "Disable autoRotate or wait for secret generators.",
                        secret.getKey(),
                        secret.getId()
                );
                skipped++;
            }
        }

        log.info(
                "Completed scheduled secret rotation check. Candidates={}, skippedUntilGenerators={}",
                secretsNeedingRotation.size(),
                skipped
        );
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

package com.maheshshinde.CryptEnv.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Drains the provider sync queue. Claims due jobs and executes them one at a time.
 * Backoff/DLQ handling lives in {@link ProviderSyncService#executeJob(Long)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderSyncWorker {

    private static final int MAX_JOBS_PER_TICK = 5;

    private final ProviderSyncService providerSyncService;

    @Scheduled(
            fixedDelayString = "${cryptenv.provider-sync.poll-ms:15000}",
            initialDelayString = "${cryptenv.provider-sync.initial-delay-ms:20000}")
    public void drainQueue() {
        for (int processed = 0; processed < MAX_JOBS_PER_TICK; processed++) {
            Optional<Long> jobId = providerSyncService.claimNextDueJob();
            if (jobId.isEmpty()) {
                return;
            }
            try {
                providerSyncService.executeJob(jobId.get());
            } catch (Exception e) {
                log.error("Unexpected error executing provider sync job {}", jobId.get(), e);
            }
        }
    }
}

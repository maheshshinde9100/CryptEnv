package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.ProviderSyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProviderSyncJobRepository extends JpaRepository<ProviderSyncJob, Long> {

    List<ProviderSyncJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ProviderSyncJob> findByIntegrationIdOrderByCreatedAtDesc(Long integrationId);

    List<ProviderSyncJob> findByStatusOrderByCreatedAtDesc(ProviderSyncJob.Status status);

    /**
     * Claimable jobs: PENDING/FAILED whose next attempt is due (or unset). Ordered oldest-first
     * so a backlog drains deterministically.
     */
    @Query("SELECT j FROM ProviderSyncJob j WHERE j.status IN ('PENDING','FAILED') "
            + "AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :now) ORDER BY j.createdAt ASC")
    List<ProviderSyncJob> findDueJobs(@Param("now") LocalDateTime now);

    long countByStatus(ProviderSyncJob.Status status);
}

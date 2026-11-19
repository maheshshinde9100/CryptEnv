package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.IntegrationSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IntegrationSyncLogRepository extends JpaRepository<IntegrationSyncLog, Long> {
    List<IntegrationSyncLog> findByIntegrationIdOrderByStartedAtDesc(Long integrationId);
    List<IntegrationSyncLog> findByIntegrationIdAndStatus(Long integrationId, String status);
}

package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.ProviderSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderSyncStateRepository extends JpaRepository<ProviderSyncState, Long> {

    Optional<ProviderSyncState> findByIntegrationIdAndTargetAndSecretKey(Long integrationId, String target, String secretKey);

    List<ProviderSyncState> findByEnvironmentId(Long environmentId);

    List<ProviderSyncState> findByIntegrationId(Long integrationId);

    List<ProviderSyncState> findByUserIdOrderByUpdatedAtDesc(Long userId);

    void deleteByIntegrationIdAndTargetAndSecretKey(Long integrationId, String target, String secretKey);

    void deleteByIntegrationId(Long integrationId);
}

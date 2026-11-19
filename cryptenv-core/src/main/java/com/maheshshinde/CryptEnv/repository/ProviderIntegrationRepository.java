package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.ProviderIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderIntegrationRepository extends JpaRepository<ProviderIntegration, Long> {
    List<ProviderIntegration> findByUserIdOrderByCreatedAtAsc(Long userId);
    Optional<ProviderIntegration> findByUserIdAndProvider(Long userId, String provider);
    Optional<ProviderIntegration> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndProvider(Long userId, String provider);
}

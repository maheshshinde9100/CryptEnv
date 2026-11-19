package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.OAuthConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthConnectionRepository extends JpaRepository<OAuthConnection, Long> {
    Optional<OAuthConnection> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<OAuthConnection> findByUserId(Long userId);
    List<OAuthConnection> findByProvider(String provider);
    void deleteByUserId(Long userId);
}

package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.SecretVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SecretVersionRepository extends JpaRepository<SecretVersion, Long> {

    List<SecretVersion> findBySecretKeyOrderByVersionNumberDesc(String secretKey);

    Optional<SecretVersion> findBySecretKeyAndVersionNumber(String secretKey, Integer versionNumber);

    Optional<SecretVersion> findBySecretKeyAndIsActiveTrue(String secretKey);

    Page<SecretVersion> findBySecretKeyOrderByCreatedAtDesc(String secretKey, Pageable pageable);

    @Query("SELECT sv FROM SecretVersion sv WHERE sv.isActive = true AND sv.expiresAt IS NOT NULL AND sv.expiresAt < :now")
    List<SecretVersion> findActiveExpiredVersions(@Param("now") LocalDateTime now);

    @Query("SELECT sv FROM SecretVersion sv WHERE sv.createdAt < :beforeDate")
    List<SecretVersion> findVersionsOlderThan(@Param("beforeDate") LocalDateTime beforeDate);

    void deleteBySecretKey(String secretKey);
}

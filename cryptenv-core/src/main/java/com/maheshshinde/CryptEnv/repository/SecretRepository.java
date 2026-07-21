package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.Secret;
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
public interface SecretRepository extends JpaRepository<Secret, Long> {

    Optional<Secret> findByKey(String key);

    List<Secret> findByIsDeletedFalse();

    Page<Secret> findByIsDeletedFalse(Pageable pageable);

    @Query("SELECT s FROM Secret s WHERE s.isDeleted = false AND s.isActive = true")
    List<Secret> findActiveSecrets();

    @Query("SELECT s FROM Secret s WHERE s.isDeleted = false AND s.autoRotate = true AND s.nextRotationAt <= :now")
    List<Secret> findSecretsNeedingRotation(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Secret s WHERE s.isDeleted = false AND s.expiresAt IS NOT NULL AND s.expiresAt <= :now")
    List<Secret> findExpiredSecrets(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Secret s WHERE s.isDeleted = true AND s.deletedAt < :beforeDate")
    List<Secret> findSoftDeletedOlderThan(@Param("beforeDate") LocalDateTime beforeDate);

    List<Secret> findByWorkspaceId(Long workspaceId);

    List<Secret> findByEnvironmentId(Long environmentId);
}

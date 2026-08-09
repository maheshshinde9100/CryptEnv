package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.Secret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecretRepository extends JpaRepository<Secret, Long> {

    Optional<Secret> findByKey(String key);

    Optional<Secret> findByEnvironmentIdAndKey(Long environmentId, String key);

    List<Secret> findByEnvironmentId(Long environmentId);

    boolean existsByKey(String key);

    boolean existsByEnvironmentIdAndKey(Long environmentId, String key);

    @Query("SELECT s FROM Secret s WHERE s.environment.workspace.owner.id = :ownerId")
    List<Secret> findByEnvironmentWorkspaceOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT s FROM Secret s WHERE s.key = :key AND s.environment.workspace.owner.id = :ownerId")
    List<Secret> findByKeyAndEnvironmentWorkspaceOwnerId(@Param("key") String key, @Param("ownerId") Long ownerId);

    List<Secret> findByAutoRotateTrueAndNextRotationAtLessThanEqual(java.time.LocalDateTime now);

    List<Secret> findByIsActiveTrueAndExpiresAtLessThanEqual(java.time.LocalDateTime now);

    List<Secret> findByIsDeletedTrueAndUpdatedAtLessThanEqual(java.time.LocalDateTime cutoffDate);
}

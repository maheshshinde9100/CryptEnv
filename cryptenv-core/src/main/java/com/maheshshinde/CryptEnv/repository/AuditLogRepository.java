package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Optional<AuditLog> findTopByOrderByIdDesc();

    List<AuditLog> findAllByOrderByIdAsc();

    Page<AuditLog> findByWorkspaceIdOrderByTimestampDesc(Long workspaceId, Pageable pageable);

    Page<AuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    Page<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(String resourceType, String resourceId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.user.id = :userId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) AND " +
           "(:success IS NULL OR a.success = :success)")
    Page<AuditLog> findByFilters(
            @Param("userId") Long userId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("success") Boolean success,
            Pageable pageable
    );

    /** Deletes audit rows older than the cutoff (retention cleanup). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
    int deleteByTimestampBefore(@Param("cutoff") LocalDateTime cutoff);
}

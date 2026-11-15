package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.DomainEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

    Page<DomainEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<DomainEvent> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);

    @Query("SELECT e FROM DomainEvent e WHERE e.workspaceId IN :workspaceIds ORDER BY e.createdAt DESC")
    List<DomainEvent> findRecentByWorkspaceIds(@Param("workspaceIds") List<Long> workspaceIds, Pageable pageable);

    List<DomainEvent> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Outbox rows ready for dispatch, oldest first. */
    @Query("SELECT e FROM DomainEvent e WHERE e.status IN ('PENDING','FAILED') "
            + "AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now) ORDER BY e.createdAt ASC")
    List<DomainEvent> findDueEvents(@Param("now") LocalDateTime now, Pageable pageable);

    long countByStatus(DomainEvent.Status status);
}

package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.JitRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JitRequestRepository extends JpaRepository<JitRequest, Long> {
    List<JitRequest> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);
    List<JitRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    List<JitRequest> findByStatusAndEndsAtBefore(JitRequest.Status status, LocalDateTime cutoff);

    @Query("SELECT j FROM JitRequest j WHERE j.environment.id = :envId AND j.requester.id = :userId "
            + "AND j.status = 'APPROVED' AND j.startsAt <= :now AND j.endsAt > :now")
    Optional<JitRequest> findActiveGrant(@Param("envId") Long envId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
}

package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<ApprovalRequest> findByWorkspaceIdInAndStatusOrderByCreatedAtDesc(Collection<Long> workspaceIds,
                                                                          ApprovalRequest.Status status);

    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalRequest.Status status);

    @Modifying
    @Query("update ApprovalRequest r set r.status = :expired "
            + "where r.status = :pending and r.expiresAt is not null and r.expiresAt < :now")
    int expireStale(@Param("pending") ApprovalRequest.Status pending,
                    @Param("expired") ApprovalRequest.Status expired,
                    @Param("now") LocalDateTime now);
}

package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.SecurityIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, Long> {

    List<SecurityIncident> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    Optional<SecurityIncident> findByIdAndReporterId(Long id, Long reporterId);

    @Query("""
            SELECT i FROM SecurityIncident i
            WHERE i.reporter.id = :userId
               OR i.workspaceId IN :workspaceIds
            ORDER BY i.createdAt DESC
            """)
    List<SecurityIncident> findVisibleTo(@Param("userId") Long userId,
                                         @Param("workspaceIds") Collection<Long> workspaceIds);
}

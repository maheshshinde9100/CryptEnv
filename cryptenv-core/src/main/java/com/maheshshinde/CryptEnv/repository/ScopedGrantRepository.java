package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.ScopedGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScopedGrantRepository extends JpaRepository<ScopedGrant, Long> {
    List<ScopedGrant> findByWorkspaceMemberId(Long workspaceMemberId);
    List<ScopedGrant> findByProjectId(Long projectId);
    List<ScopedGrant> findByEnvironmentId(Long environmentId);
    List<ScopedGrant> findByWorkspaceMemberIdAndProjectId(Long workspaceMemberId, Long projectId);
    List<ScopedGrant> findByWorkspaceMemberIdAndEnvironmentId(Long workspaceMemberId, Long environmentId);
    void deleteByWorkspaceMemberId(Long workspaceMemberId);
}

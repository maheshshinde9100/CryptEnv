package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.SecretBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecretBranchRepository extends JpaRepository<SecretBranch, Long> {
    List<SecretBranch> findByWorkspaceId(Long workspaceId);
    List<SecretBranch> findByProjectId(Long projectId);
    List<SecretBranch> findByEnvironmentId(Long environmentId);
    Optional<SecretBranch> findByWorkspaceIdAndName(Long workspaceId, String name);
    List<SecretBranch> findByWorkspaceIdAndIsActive(Long workspaceId, Boolean isActive);
}

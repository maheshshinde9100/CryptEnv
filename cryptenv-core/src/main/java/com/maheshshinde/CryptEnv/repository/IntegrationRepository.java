package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.Integration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntegrationRepository extends JpaRepository<Integration, Long> {
    List<Integration> findByWorkspaceId(Long workspaceId);
    List<Integration> findByWorkspaceIdAndCategory(Long workspaceId, String category);
    Optional<Integration> findByWorkspaceIdAndIntegrationType(Long workspaceId, String integrationType);
    void deleteByWorkspaceId(Long workspaceId);
}

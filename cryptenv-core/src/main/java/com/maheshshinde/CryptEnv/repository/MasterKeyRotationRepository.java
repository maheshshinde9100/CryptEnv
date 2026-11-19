package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.MasterKeyRotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasterKeyRotationRepository extends JpaRepository<MasterKeyRotation, Long> {
    List<MasterKeyRotation> findByWorkspaceId(Long workspaceId);
    Optional<MasterKeyRotation> findByWorkspaceIdAndStatus(Long workspaceId, String status);
}

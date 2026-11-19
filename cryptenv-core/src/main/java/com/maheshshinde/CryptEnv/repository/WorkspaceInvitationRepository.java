package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, Long> {
    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);
    List<WorkspaceInvitation> findByWorkspaceIdAndAcceptedAtIsNull(Long workspaceId);
}

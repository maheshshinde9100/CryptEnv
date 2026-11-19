package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByWorkspaceId(Long workspaceId);
    Optional<Project> findByWorkspaceIdAndName(Long workspaceId, String name);
    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);
    Optional<Project> findByIdAndWorkspaceId(Long id, Long workspaceId);
}

package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long>, JpaSpecificationExecutor<Workspace> {

    Optional<Workspace> findByName(String name);

    Boolean existsByName(String name);

    List<Workspace> findByOwnerId(Long ownerId);

    List<Workspace> findByMembersId(Long userId);
}

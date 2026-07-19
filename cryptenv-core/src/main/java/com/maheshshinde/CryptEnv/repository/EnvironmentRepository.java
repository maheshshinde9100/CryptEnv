package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, Long>, JpaSpecificationExecutor<Environment> {

    List<Environment> findByWorkspaceId(Long workspaceId);

    Optional<Environment> findByWorkspaceIdAndName(Long workspaceId, Environment.EnvironmentType name);
}

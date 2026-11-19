package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.MachineIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MachineIdentityRepository extends JpaRepository<MachineIdentity, Long> {
    List<MachineIdentity> findByWorkspaceId(Long workspaceId);
    List<MachineIdentity> findByStatus(String status);
}

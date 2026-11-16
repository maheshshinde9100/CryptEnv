package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.WorkloadKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkloadKeyRepository extends JpaRepository<WorkloadKey, Long> {
    List<WorkloadKey> findByMachineIdentityId(Long machineIdentityId);
    List<WorkloadKey> findByProjectId(Long projectId);
    void deleteByMachineIdentityId(Long machineIdentityId);
}

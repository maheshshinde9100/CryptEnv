package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.SecretBranchVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecretBranchVersionRepository extends JpaRepository<SecretBranchVersion, Long> {
    List<SecretBranchVersion> findBySecretBranchId(Long secretBranchId);
    Optional<SecretBranchVersion> findBySecretBranchIdAndSecretId(Long secretBranchId, Long secretId);
    void deleteBySecretBranchId(Long secretBranchId);
}

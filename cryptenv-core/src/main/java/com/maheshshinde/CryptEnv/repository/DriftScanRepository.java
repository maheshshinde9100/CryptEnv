package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.DriftScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriftScanRepository extends JpaRepository<DriftScan, Long> {

    List<DriftScan> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<DriftScan> findByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

    Optional<DriftScan> findByIdAndUserId(Long id, Long userId);

    List<DriftScan> findByEnvironmentIdAndStatusOrderByCreatedAtDesc(Long environmentId, DriftScan.Status status);
}

package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.DriftFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriftFindingRepository extends JpaRepository<DriftFinding, Long> {

    List<DriftFinding> findByScanIdOrderByClassificationAscSecretKeyAsc(Long scanId);

    List<DriftFinding> findByScanIdAndClassificationOrderBySecretKeyAsc(Long scanId, DriftFinding.Classification classification);

    long countByScanId(Long scanId);

    void deleteByScanId(Long scanId);
}

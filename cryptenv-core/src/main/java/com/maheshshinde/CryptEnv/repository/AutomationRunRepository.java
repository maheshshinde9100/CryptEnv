package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.AutomationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    Page<AutomationRun> findByRuleIdOrderByCreatedAtDesc(Long ruleId, Pageable pageable);

    Page<AutomationRun> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

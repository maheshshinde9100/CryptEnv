package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {

    List<AutomationRule> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AutomationRule> findByUserIdAndEnabledTrue(Long userId);

    Optional<AutomationRule> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndEnabledTrue(Long userId);
}

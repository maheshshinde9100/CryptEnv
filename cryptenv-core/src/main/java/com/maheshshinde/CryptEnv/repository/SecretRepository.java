package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.Secret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecretRepository extends JpaRepository<Secret, Long> {
    Optional<Secret> findByKey(String key);
    List<Secret> findByEnvironmentId(Long environmentId);
    boolean existsByKey(String key);
}

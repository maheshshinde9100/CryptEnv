package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.SecretReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecretReferenceRepository extends JpaRepository<SecretReference, Long> {
    List<SecretReference> findBySecretId(Long secretId);
    List<SecretReference> findByTargetSecretId(Long targetSecretId);
    void deleteBySecretId(Long secretId);
}

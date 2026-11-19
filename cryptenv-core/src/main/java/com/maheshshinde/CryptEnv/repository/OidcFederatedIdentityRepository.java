package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.OidcFederatedIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OidcFederatedIdentityRepository extends JpaRepository<OidcFederatedIdentity, Long> {
    Optional<OidcFederatedIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
    void deleteByMachineIdentityId(Long machineIdentityId);
}

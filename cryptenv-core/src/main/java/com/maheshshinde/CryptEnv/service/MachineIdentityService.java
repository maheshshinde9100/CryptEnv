package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.MachineIdentity;
import com.maheshshinde.CryptEnv.model.OidcFederatedIdentity;
import com.maheshshinde.CryptEnv.model.WorkloadKey;
import com.maheshshinde.CryptEnv.repository.MachineIdentityRepository;
import com.maheshshinde.CryptEnv.repository.OidcFederatedIdentityRepository;
import com.maheshshinde.CryptEnv.repository.WorkloadKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MachineIdentityService {

    private final MachineIdentityRepository machineIdentityRepository;
    private final OidcFederatedIdentityRepository oidcFederatedIdentityRepository;
    private final WorkloadKeyRepository workloadKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public MachineIdentity createMachineIdentity(Long workspaceId, String name, String description,
                                                 String type, String scopes, LocalDateTime expiresAt, Long createdBy) {
        MachineIdentity identity = MachineIdentity.builder()
                .workspaceId(workspaceId)
                .name(name)
                .description(description)
                .type(type)
                .scopes(scopes)
                .expiresAt(expiresAt)
                .status("active")
                .createdBy(createdBy)
                .build();
        return machineIdentityRepository.save(identity);
    }

    @Transactional
    public OidcFederatedIdentity linkOidcIdentity(Long machineIdentityId, String provider,
                                                 String providerSubject, String issuerUrl, String audience) {
        OidcFederatedIdentity identity = OidcFederatedIdentity.builder()
                .machineIdentityId(machineIdentityId)
                .provider(provider)
                .providerSubject(providerSubject)
                .issuerUrl(issuerUrl)
                .audience(audience)
                .build();
        return oidcFederatedIdentityRepository.save(identity);
    }

    @Transactional
    public WorkloadKey createWorkloadKey(Long machineIdentityId, Long projectId, String wrappedPrivateKey) {
        byte[] publicKeyBytes = new byte[32];
        secureRandom.nextBytes(publicKeyBytes);
        String publicKey = Base64.getEncoder().encodeToString(publicKeyBytes);
        
        WorkloadKey workloadKey = WorkloadKey.builder()
                .machineIdentityId(machineIdentityId)
                .projectId(projectId)
                .publicKey(publicKey)
                .wrappedPrivateKey(wrappedPrivateKey)
                .build();
        return workloadKeyRepository.save(workloadKey);
    }

    @Transactional
    public void revokeMachineIdentity(Long machineIdentityId) {
        MachineIdentity identity = machineIdentityRepository.findById(machineIdentityId)
                .orElseThrow(() -> new IllegalArgumentException("Machine identity not found"));
        identity.setStatus("revoked");
        machineIdentityRepository.save(identity);
    }

    public List<MachineIdentity> getWorkspaceMachineIdentities(Long workspaceId) {
        return machineIdentityRepository.findByWorkspaceId(workspaceId);
    }

    public List<WorkloadKey> getMachineIdentityWorkloadKeys(Long machineIdentityId) {
        return workloadKeyRepository.findByMachineIdentityId(machineIdentityId);
    }
}

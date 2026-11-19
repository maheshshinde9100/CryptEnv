package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.SecretBranch;
import com.maheshshinde.CryptEnv.model.SecretBranchVersion;
import com.maheshshinde.CryptEnv.repository.SecretBranchRepository;
import com.maheshshinde.CryptEnv.repository.SecretBranchVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretBranchService {

    private final SecretBranchRepository secretBranchRepository;
    private final SecretBranchVersionRepository secretBranchVersionRepository;

    @Transactional
    public SecretBranch createBranch(Long workspaceId, Long projectId, Long environmentId, String name,
                                    Long createdBy, Long parentBranchId) {
        SecretBranch branch = SecretBranch.builder()
                .workspaceId(workspaceId)
                .projectId(projectId)
                .environmentId(environmentId)
                .name(name)
                .isActive(false)
                .createdBy(createdBy)
                .parentBranchId(parentBranchId)
                .build();
        return secretBranchRepository.save(branch);
    }

    @Transactional
    public SecretBranch activateBranch(Long branchId) {
        SecretBranch branch = secretBranchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
        
        secretBranchRepository.findByWorkspaceIdAndIsActive(branch.getWorkspaceId(), true)
                .forEach(b -> {
                    b.setIsActive(false);
                    secretBranchRepository.save(b);
                });
        
        branch.setIsActive(true);
        return secretBranchRepository.save(branch);
    }

    @Transactional
    public SecretBranchVersion addSecretToBranch(Long branchId, Long secretId, Long secretVersionId) {
        SecretBranchVersion version = SecretBranchVersion.builder()
                .secretBranchId(branchId)
                .secretId(secretId)
                .secretVersionId(secretVersionId)
                .build();
        return secretBranchVersionRepository.save(version);
    }

    @Transactional
    public void deleteBranch(Long branchId) {
        secretBranchVersionRepository.deleteBySecretBranchId(branchId);
        secretBranchRepository.deleteById(branchId);
    }

    public List<SecretBranch> getWorkspaceBranches(Long workspaceId) {
        return secretBranchRepository.findByWorkspaceId(workspaceId);
    }

    public List<SecretBranchVersion> getBranchVersions(Long branchId) {
        return secretBranchVersionRepository.findBySecretBranchId(branchId);
    }

    public SecretBranch getActiveBranch(Long workspaceId) {
        return secretBranchRepository.findByWorkspaceIdAndIsActive(workspaceId, true)
                .stream()
                .findFirst()
                .orElse(null);
    }
}

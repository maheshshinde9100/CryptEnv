package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.ScopedGrant;
import com.maheshshinde.CryptEnv.repository.ScopedGrantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScopedGrantService {

    private final ScopedGrantRepository scopedGrantRepository;

    @Transactional
    public ScopedGrant createGrant(Long workspaceMemberId, Long projectId, Long environmentId,
                                   String grantType, String permissions, Long grantedBy,
                                   LocalDateTime expiresAt) {
        ScopedGrant grant = ScopedGrant.builder()
                .workspaceMemberId(workspaceMemberId)
                .projectId(projectId)
                .environmentId(environmentId)
                .grantType(grantType)
                .permissions(permissions)
                .grantedBy(grantedBy)
                .expiresAt(expiresAt)
                .build();
        return scopedGrantRepository.save(grant);
    }

    @Transactional
    public void revokeGrant(Long grantId) {
        scopedGrantRepository.deleteById(grantId);
    }

    @Transactional
    public void revokeAllMemberGrants(Long workspaceMemberId) {
        scopedGrantRepository.deleteByWorkspaceMemberId(workspaceMemberId);
    }

    public List<ScopedGrant> getMemberGrants(Long workspaceMemberId) {
        return scopedGrantRepository.findByWorkspaceMemberId(workspaceMemberId);
    }

    public List<ScopedGrant> getProjectGrants(Long projectId) {
        return scopedGrantRepository.findByProjectId(projectId);
    }

    public List<ScopedGrant> getEnvironmentGrants(Long environmentId) {
        return scopedGrantRepository.findByEnvironmentId(environmentId);
    }

    public boolean hasPermission(Long workspaceMemberId, Long projectId, Long environmentId, String permission) {
        List<ScopedGrant> grants = scopedGrantRepository.findByWorkspaceMemberId(workspaceMemberId);
        for (ScopedGrant grant : grants) {
            if ((projectId == null || grant.getProjectId() == null || grant.getProjectId().equals(projectId)) &&
                (environmentId == null || grant.getEnvironmentId() == null || grant.getEnvironmentId().equals(environmentId))) {
                if (grant.getExpiresAt() == null || grant.getExpiresAt().isAfter(LocalDateTime.now())) {
                    if (grant.getPermissions().contains(permission)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

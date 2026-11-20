package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.*;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.JitRequestRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JitAccessService {

    private final JitRequestRepository jitRequestRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EnvironmentRepository environmentRepository;
    private final WorkspaceService workspaceService;
    private final SecurityService securityService;

    @Transactional
    public Map<String, Object> requestAccess(Long workspaceId, Long environmentId, String justification, int hours) {
        if (hours < 1 || hours > 24) {
            throw new IllegalArgumentException("Duration must be 1–24 hours");
        }
        User user = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (workspaceService.getEffectiveRole(workspace, user) == null) {
            throw new RuntimeException("Access denied");
        }
        Environment env = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found"));
        if (!env.getWorkspace().getId().equals(workspaceId)) {
            throw new ResourceNotFoundException("Environment not found in workspace");
        }

        JitRequest req = JitRequest.builder()
                .workspace(workspace)
                .environment(env)
                .requester(user)
                .justification(justification)
                .durationHours(hours)
                .status(JitRequest.Status.PENDING)
                .build();
        req = jitRequestRepository.save(req);
        return toMap(req);
    }

    @Transactional
    public Map<String, Object> approve(Long requestId) {
        User admin = securityService.getCurrentUser();
        JitRequest req = jitRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("JIT request not found"));
        Role role = workspaceService.getEffectiveRole(req.getWorkspace(), admin);
        if (role != Role.OWNER && role != Role.ADMIN) {
            throw new RuntimeException("Only owner/admin can approve JIT");
        }
        if (req.getStatus() != JitRequest.Status.PENDING) {
            throw new IllegalArgumentException("Request is not pending");
        }
        LocalDateTime now = LocalDateTime.now();
        req.setStatus(JitRequest.Status.APPROVED);
        req.setApprovedBy(admin);
        req.setStartsAt(now);
        req.setEndsAt(now.plusHours(req.getDurationHours()));
        return toMap(jitRequestRepository.save(req));
    }

    @Transactional
    public Map<String, Object> deny(Long requestId) {
        User admin = securityService.getCurrentUser();
        JitRequest req = jitRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("JIT request not found"));
        Role role = workspaceService.getEffectiveRole(req.getWorkspace(), admin);
        if (role != Role.OWNER && role != Role.ADMIN) {
            throw new RuntimeException("Only owner/admin can deny JIT");
        }
        if (req.getStatus() != JitRequest.Status.PENDING) {
            throw new IllegalArgumentException("Request is not pending");
        }
        req.setStatus(JitRequest.Status.DENIED);
        req.setApprovedBy(admin);
        return toMap(jitRequestRepository.save(req));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForWorkspace(Long workspaceId) {
        User user = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (workspaceService.getEffectiveRole(workspace, user) == null) {
            throw new ResourceNotFoundException("Workspace not found");
        }
        return jitRequestRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveGrant(Long environmentId, Long userId) {
        return jitRequestRepository.findActiveGrant(environmentId, userId, LocalDateTime.now()).isPresent();
    }

    @Scheduled(cron = "0 */15 * * * ?")
    @Transactional
    public void expireGrants() {
        List<JitRequest> expired = jitRequestRepository.findByStatusAndEndsAtBefore(
                JitRequest.Status.APPROVED, LocalDateTime.now());
        for (JitRequest req : expired) {
            req.setStatus(JitRequest.Status.EXPIRED);
        }
        if (!expired.isEmpty()) {
            jitRequestRepository.saveAll(expired);
            log.info("Expired {} JIT grants", expired.size());
        }
    }

    private Map<String, Object> toMap(JitRequest r) {
        return Map.of(
                "id", r.getId(),
                "workspaceId", r.getWorkspace().getId(),
                "environmentId", r.getEnvironment().getId(),
                "requesterId", r.getRequester().getId(),
                "justification", r.getJustification(),
                "status", r.getStatus().name(),
                "durationHours", r.getDurationHours(),
                "startsAt", r.getStartsAt() != null ? r.getStartsAt().toString() : "",
                "endsAt", r.getEndsAt() != null ? r.getEndsAt().toString() : ""
        );
    }
}

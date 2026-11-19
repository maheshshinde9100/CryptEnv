package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.ApprovalRequest;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.model.WorkspaceMember;
import com.maheshshinde.CryptEnv.repository.ApprovalRequestRepository;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceMemberRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Four-eyes approval (§10) for destructive actions.
 * <p>
 * A user raises a request; a <em>different</em> eligible user (workspace owner, a workspace
 * member with the OWNER/ADMIN role, or a global admin) approves or rejects it. Approved actions
 * are executed by the matching {@link ApprovalActionExecutor}. A requester can never approve
 * their own request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    public static final String ACTION_SECRET_DELETE = "SECRET_DELETE";

    private final ApprovalRequestRepository approvalRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecurityService securityService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final List<ApprovalActionExecutor> executors;

    @Value("${cryptenv.approvals.enabled:true}")
    boolean approvalsEnabled;

    @Value("${cryptenv.approvals.ttl-hours:24}")
    int ttlHours;

    // ------------------------------------------------------------------ request

    @Transactional
    public Map<String, Object> request(String action, Long workspaceId, String resourceType, String resourceId,
                                       Map<String, Object> payload, String reason, Integer ttlHoursOverride) {
        requireEnabled();
        User requester = securityService.getCurrentUser();
        findExecutor(action);

        Long scopeId = resolveWorkspaceId(workspaceId, payload);
        if (scopeId != null) {
            if (!canAccessWorkspace(scopeId, requester)) {
                throw new IllegalArgumentException("You do not have access to this workspace");
            }
            if (eligibleApprovers(scopeId, requester.getId()).isEmpty()) {
                throw new IllegalStateException(
                        "Four-eyes approval requires another owner/admin in this workspace");
            }
        }

        int ttl = ttlHoursOverride != null && ttlHoursOverride > 0 ? ttlHoursOverride : ttlHours;
        ApprovalRequest request = ApprovalRequest.builder()
                .requesterId(requester.getId())
                .workspaceId(scopeId)
                .action(action.trim().toUpperCase())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .payload(writeJson(payload))
                .reason(reason)
                .status(ApprovalRequest.Status.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(ttl))
                .build();
        ApprovalRequest saved = approvalRepository.save(request);
        auditLogService.logEvent(requester, "APPROVAL_REQUEST", "APPROVAL", String.valueOf(saved.getId()), true,
                null, "Requested " + saved.getAction() + " approval", scopeId);
        return toView(saved);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMine() {
        User user = securityService.getCurrentUser();
        return approvalRepository.findByRequesterIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toView)
                .toList();
    }

    /** Pending requests raised by others in workspaces the caller can approve for. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPendingForMe() {
        User user = securityService.getCurrentUser();
        Set<Long> workspaceIds = accessibleWorkspaceIds(user);
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return approvalRepository
                .findByWorkspaceIdInAndStatusOrderByCreatedAtDesc(workspaceIds, ApprovalRequest.Status.PENDING).stream()
                .filter(r -> !r.getRequesterId().equals(user.getId()))
                .filter(r -> isEligibleApprover(r.getWorkspaceId(), user))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        return toView(loadForCaller(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> supportedActions() {
        return executors.stream()
                .map(e -> Map.<String, Object>of("action", e.action()))
                .toList();
    }

    // ------------------------------------------------------------------ decisions

    @Transactional
    public Map<String, Object> decide(Long id, boolean approve, String note) {
        requireEnabled();
        User approver = securityService.getCurrentUser();
        ApprovalRequest request = approvalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found: " + id));

        if (request.getStatus() != ApprovalRequest.Status.PENDING) {
            throw new IllegalStateException("Approval request is already " + request.getStatus());
        }
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus(ApprovalRequest.Status.EXPIRED);
            approvalRepository.save(request);
            throw new IllegalStateException("Approval request has expired");
        }
        if (request.getRequesterId().equals(approver.getId())) {
            throw new IllegalStateException("Four-eyes: the requester cannot decide their own request");
        }
        if (!isEligibleApprover(request.getWorkspaceId(), approver)) {
            throw new IllegalStateException("You are not an eligible approver for this request");
        }

        request.setApproverId(approver.getId());
        request.setDecisionNote(note);
        request.setDecidedAt(LocalDateTime.now());

        if (!approve) {
            request.setStatus(ApprovalRequest.Status.REJECTED);
            ApprovalRequest saved = approvalRepository.save(request);
            auditLogService.logEvent(approver, "APPROVAL_REJECT", "APPROVAL", id.toString(), true, null,
                    "Rejected " + saved.getAction() + " request", saved.getWorkspaceId());
            return toView(saved);
        }

        request.setStatus(ApprovalRequest.Status.APPROVED);
        Optional<ApprovalActionExecutor> executor = executorFor(request.getAction());
        if (executor.isEmpty()) {
            request.setStatus(ApprovalRequest.Status.FAILED);
            request.setResultDetail("No executor registered for action " + request.getAction());
        } else {
            try {
                request.setResultDetail(executor.get().execute(request));
                request.setStatus(ApprovalRequest.Status.EXECUTED);
                request.setExecutedAt(LocalDateTime.now());
            } catch (Exception e) {
                log.warn("Approved action {} for request {} failed: {}", request.getAction(), id, e.getMessage());
                request.setStatus(ApprovalRequest.Status.FAILED);
                request.setResultDetail(e.getMessage());
            }
        }
        ApprovalRequest saved = approvalRepository.save(request);
        auditLogService.logEvent(approver, "APPROVAL_APPROVE", "APPROVAL", id.toString(),
                saved.getStatus() == ApprovalRequest.Status.EXECUTED, null,
                "Approved " + saved.getAction() + " request -> " + saved.getStatus(), saved.getWorkspaceId());
        return toView(saved);
    }

    @Transactional
    public Map<String, Object> cancel(Long id) {
        User user = securityService.getCurrentUser();
        ApprovalRequest request = approvalRepository.findById(id)
                .filter(r -> r.getRequesterId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found: " + id));
        if (request.getStatus() != ApprovalRequest.Status.PENDING) {
            throw new IllegalStateException("Approval request is already " + request.getStatus());
        }
        request.setStatus(ApprovalRequest.Status.CANCELLED);
        request.setDecidedAt(LocalDateTime.now());
        ApprovalRequest saved = approvalRepository.save(request);
        auditLogService.logEvent(user, "APPROVAL_CANCEL", "APPROVAL", id.toString(), true, null,
                "Cancelled " + saved.getAction() + " request", saved.getWorkspaceId());
        return toView(saved);
    }

    /** Expires stale pending requests so they cannot be approved after their deadline. */
    @Scheduled(fixedDelayString = "${cryptenv.approvals.sweep-ms:300000}",
            initialDelayString = "${cryptenv.approvals.sweep-initial-delay-ms:60000}")
    @Transactional
    public void expireStaleRequests() {
        int expired = approvalRepository.expireStale(ApprovalRequest.Status.PENDING,
                ApprovalRequest.Status.EXPIRED, LocalDateTime.now());
        if (expired > 0) {
            log.info("Expired {} stale approval request(s)", expired);
        }
    }

    // ------------------------------------------------------------------ eligibility

    private void requireEnabled() {
        if (!approvalsEnabled) {
            throw new IllegalStateException("Approvals are disabled on this deployment");
        }
    }

    private Optional<ApprovalActionExecutor> executorFor(String action) {
        return executors.stream().filter(e -> e.action().equalsIgnoreCase(action)).findFirst();
    }

    private ApprovalActionExecutor findExecutor(String action) {
        return executorFor(action)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported approval action: " + action));
    }

    private boolean isEligibleApprover(Long workspaceId, User user) {
        if (user.getRole() == Role.OWNER || user.getRole() == Role.ADMIN) {
            return true;
        }
        if (workspaceId == null) {
            return false;
        }
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            return false;
        }
        if (workspace.getOwner() != null && workspace.getOwner().getId().equals(user.getId())) {
            return true;
        }
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .map(WorkspaceMember::getRole)
                .map(role -> role == Role.OWNER || role == Role.ADMIN)
                .orElse(false);
    }

    private List<User> eligibleApprovers(Long workspaceId, Long requesterId) {
        List<User> approvers = new ArrayList<>();
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            return approvers;
        }
        if (workspace.getOwner() != null && !workspace.getOwner().getId().equals(requesterId)) {
            approvers.add(workspace.getOwner());
        }
        for (WorkspaceMember member : memberRepository.findByWorkspaceId(workspaceId)) {
            User candidate = member.getUser();
            if (candidate == null || candidate.getId().equals(requesterId)) {
                continue;
            }
            if (member.getRole() == Role.OWNER || member.getRole() == Role.ADMIN) {
                approvers.add(candidate);
            }
        }
        return approvers;
    }

    private boolean canAccessWorkspace(Long workspaceId, User user) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            return false;
        }
        if (workspace.getOwner() != null && workspace.getOwner().getId().equals(user.getId())) {
            return true;
        }
        return memberRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId());
    }

    private Set<Long> accessibleWorkspaceIds(User user) {
        Set<Long> ids = new LinkedHashSet<>();
        workspaceRepository.findByOwnerId(user.getId()).forEach(w -> ids.add(w.getId()));
        workspaceRepository.findByMembershipsUserId(user.getId()).forEach(w -> ids.add(w.getId()));
        return ids;
    }

    private Long resolveWorkspaceId(Long workspaceId, Map<String, Object> payload) {
        if (workspaceId != null) {
            return workspaceId;
        }
        Long environmentId = longValue(payload != null ? payload.get("environmentId") : null);
        if (environmentId == null) {
            return null;
        }
        return environmentRepository.findById(environmentId)
                .map(Environment::getWorkspace)
                .map(Workspace::getId)
                .orElse(null);
    }

    private ApprovalRequest loadForCaller(Long id) {
        User user = securityService.getCurrentUser();
        ApprovalRequest request = approvalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found: " + id));
        boolean involved = request.getRequesterId().equals(user.getId())
                || (request.getApproverId() != null && request.getApproverId().equals(user.getId()));
        if (!involved && !isEligibleApprover(request.getWorkspaceId(), user)) {
            throw new ResourceNotFoundException("Approval request not found: " + id);
        }
        return request;
    }

    // ------------------------------------------------------------------ views

    private Map<String, Object> toView(ApprovalRequest request) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", request.getId());
        view.put("requesterId", request.getRequesterId());
        view.put("workspaceId", request.getWorkspaceId());
        view.put("action", request.getAction());
        view.put("resourceType", request.getResourceType());
        view.put("resourceId", request.getResourceId());
        view.put("payload", readJson(request.getPayload()));
        view.put("reason", request.getReason());
        view.put("status", request.getStatus().name());
        view.put("approverId", request.getApproverId());
        view.put("decisionNote", request.getDecisionNote());
        view.put("resultDetail", request.getResultDetail());
        view.put("decidedAt", request.getDecidedAt());
        view.put("executedAt", request.getExecutedAt());
        view.put("expiresAt", request.getExpiresAt());
        view.put("createdAt", request.getCreatedAt());
        return view;
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

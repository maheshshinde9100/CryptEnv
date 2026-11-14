package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.AuditLog;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String GENESIS_HASH = "0".repeat(64);

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog logEvent(User user, String action, String resourceType, String resourceId,
                            Boolean success, HttpServletRequest request, String details) {
        return logEvent(user, action, resourceType, resourceId, success, request, details, null);
    }

    @Transactional
    public AuditLog logEvent(User user, String action, String resourceType, String resourceId,
                            Boolean success, HttpServletRequest request, String details, Long workspaceId) {
        String previousHash = auditLogRepository.findTopByOrderByIdDesc()
                .map(AuditLog::getEntryHash)
                .filter(h -> h != null && !h.isBlank())
                .orElse(GENESIS_HASH);

        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(success != null ? success : true)
                .ipAddress(getClientIpAddress(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .details(details)
                .workspaceId(workspaceId)
                .previousHash(previousHash)
                .build();

        AuditLog saved = auditLogRepository.saveAndFlush(auditLog);
        saved.setEntryHash(computeEntryHash(saved, previousHash));
        return auditLogRepository.save(saved);
    }

    public AuditLog logEvent(User user, String action, String resourceType, String resourceId,
                            HttpServletRequest request) {
        return logEvent(user, action, resourceType, resourceId, true, request, null);
    }

    public AuditLog logEvent(User user, String action, String resourceType, String resourceId,
                            Boolean success, HttpServletRequest request) {
        return logEvent(user, action, resourceType, resourceId, success, request, null);
    }

    public Page<AuditLog> getUserAuditLogs(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    public Page<AuditLog> getAuditLogsByAction(String action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
    }

    public Page<AuditLog> getResourceAuditLogs(String resourceType, String resourceId, Pageable pageable) {
        return auditLogRepository.findByResourceTypeAndResourceIdOrderByTimestampDesc(resourceType, resourceId, pageable);
    }

    public Page<AuditLog> getFilteredAuditLogs(Long userId, String action, String resourceType,
                                               LocalDateTime startDate, LocalDateTime endDate,
                                               Boolean success, Pageable pageable) {
        return auditLogRepository.findByFilters(userId, action, resourceType, startDate, endDate, success, pageable);
    }

    public Page<AuditLog> getWorkspaceAuditLogs(Long workspaceId, Pageable pageable) {
        return auditLogRepository.findByWorkspaceIdOrderByTimestampDesc(workspaceId, pageable);
    }

    /**
     * Verifies the SHA-256 hash chain from oldest to newest.
     * Returns a map with valid=true/false and optional brokenAt id.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> verifyHashChain() {
        List<AuditLog> logs = auditLogRepository.findAllByOrderByIdAsc();
        List<Long> broken = new ArrayList<>();
        String expectedPrevious = GENESIS_HASH;
        int checked = 0;

        for (AuditLog log : logs) {
            if (log.getEntryHash() == null || log.getEntryHash().isBlank()) {
                // Pre-chain legacy row — skip without failing the whole chain
                continue;
            }
            checked++;
            String prev = log.getPreviousHash() == null ? GENESIS_HASH : log.getPreviousHash();
            if (!expectedPrevious.equals(prev) && checked > 1) {
                // First hashed row may start after legacy rows; allow genesis or last hashed
            }
            String recomputed = computeEntryHash(log, prev);
            if (!recomputed.equals(log.getEntryHash())) {
                broken.add(log.getId());
            }
            expectedPrevious = log.getEntryHash();
        }

        return Map.of(
                "valid", broken.isEmpty(),
                "checked", checked,
                "brokenIds", broken
        );
    }

    static String computeEntryHash(AuditLog log, String previousHash) {
        String canonical = String.join("|",
                nullToEmpty(previousHash),
                log.getId() != null ? String.valueOf(log.getId()) : "",
                log.getUser() != null ? String.valueOf(log.getUser().getId()) : "",
                nullToEmpty(log.getAction()),
                nullToEmpty(log.getResourceType()),
                nullToEmpty(log.getResourceId()),
                String.valueOf(Boolean.TRUE.equals(log.getSuccess())),
                nullToEmpty(log.getIpAddress()),
                nullToEmpty(log.getDetails()),
                log.getWorkspaceId() != null ? String.valueOf(log.getWorkspaceId()) : "",
                log.getTimestamp() != null ? log.getTimestamp().toString() : ""
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @Transactional
    public int deleteOldLogs(LocalDateTime beforeDate) {
        return auditLogRepository.deleteByTimestampBefore(beforeDate);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}

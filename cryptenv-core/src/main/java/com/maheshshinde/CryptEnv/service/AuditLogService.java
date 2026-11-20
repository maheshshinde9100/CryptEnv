package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.AuditLogDto;
import com.maheshshinde.CryptEnv.model.AuditLog;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    public static final String GENESIS_HASH = "0".repeat(64);

    private final AuditLogRepository auditLogRepository;
    private final EventService eventService;
    private final ObjectMapper objectMapper;

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

        LocalDateTime now = LocalDateTime.now();
        AuditLog auditLog = AuditLog.builder()
                .timestamp(now)
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

        auditLog.setEntryHash(computeEntryHash(auditLog, previousHash));
        AuditLog persisted = auditLogRepository.save(auditLog);

        publishDomainEvent(user, action, resourceType, resourceId, success, details, workspaceId);
        return persisted;
    }

    /** Mirrors the audit entry into the domain event outbox (§10) for the live feed and webhooks. */
    private void publishDomainEvent(User user, String action, String resourceType, String resourceId,
                                    Boolean success, String details, Long workspaceId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", action);
            payload.put("success", success == null || success);
            if (details != null) {
                payload.put("details", details);
            }
            eventService.publish(action, user != null ? user.getId() : null, workspaceId,
                    resourceType, resourceId, payload);
        } catch (Exception e) {
            log.warn("Failed to record domain event for action {}: {}", action, e.getMessage());
        }
    }

    public AuditLog logEvent(User user, String action, String resourceType, String resourceId,
                            HttpServletRequest request) {
        return logEvent(user, action, resourceType, resourceId, true, request, null);
    }

    public AuditLog logEvent(User user, String action, String resourceType, String resourceId,
                            Boolean success, HttpServletRequest request) {
        return logEvent(user, action, resourceType, resourceId, success, request, null);
    }

    public Page<AuditLogDto> getUserAuditLogs(Long userId, Pageable pageable) {
        return toDtoPage(auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable));
    }

    public Page<AuditLogDto> getAuditLogsByAction(String action, Pageable pageable) {
        return toDtoPage(auditLogRepository.findByActionOrderByTimestampDesc(action, pageable));
    }

    public Page<AuditLogDto> getResourceAuditLogs(String resourceType, String resourceId, Pageable pageable) {
        return toDtoPage(auditLogRepository.findByResourceTypeAndResourceIdOrderByTimestampDesc(resourceType, resourceId, pageable));
    }

    private static Page<AuditLogDto> toDtoPage(Page<AuditLog> page) {
        return page.map(AuditLogDto::from);
    }

    public Page<AuditLogDto> getFilteredAuditLogs(Long workspaceId, Long userId, String action, String resourceType,
                                               LocalDateTime startDate, LocalDateTime endDate,
                                               Boolean success, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (workspaceId != null) {
                // Rows carry workspace_id when known; otherwise scope by the resource they reference.
                Subquery<String> envIds = query.subquery(String.class);
                Root<com.maheshshinde.CryptEnv.model.Environment> env = envIds.from(com.maheshshinde.CryptEnv.model.Environment.class);
                envIds.select(env.get("id").as(String.class))
                        .where(cb.equal(env.get("workspace").get("id"), workspaceId));
                Subquery<String> secretKeys = query.subquery(String.class);
                Root<com.maheshshinde.CryptEnv.model.Secret> secret = secretKeys.from(com.maheshshinde.CryptEnv.model.Secret.class);
                secretKeys.select(secret.get("key"))
                        .where(cb.equal(secret.get("environment").get("workspace").get("id"), workspaceId));
                predicates.add(cb.or(
                        cb.equal(root.get("workspaceId"), workspaceId),
                        cb.and(cb.equal(root.get("resourceType"), "WORKSPACE"),
                                cb.equal(root.get("resourceId"), String.valueOf(workspaceId))),
                        cb.and(cb.equal(root.get("resourceType"), "ENVIRONMENT"),
                                root.get("resourceId").in(envIds)),
                        cb.and(cb.equal(root.get("resourceType"), "SECRET"),
                                root.get("resourceId").in(secretKeys))
                ));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return toDtoPage(auditLogRepository.findAll(spec, pageable));
    }

    public Page<AuditLogDto> getWorkspaceAuditLogs(Long workspaceId, Pageable pageable) {
        return toDtoPage(auditLogRepository.findByWorkspaceIdOrderByTimestampDesc(workspaceId, pageable));
    }

    /** Export audit evidence without user/password/secret-value fields. */
    @Transactional(readOnly = true)
    public String export(Long workspaceId, String format) {
        List<AuditLog> logs = workspaceId == null ? auditLogRepository.findAllByOrderByIdAsc()
                : auditLogRepository.findByWorkspaceIdOrderByTimestampDesc(workspaceId, Pageable.unpaged()).getContent();
        List<Map<String, Object>> rows = logs.stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId()); row.put("timestamp", log.getTimestamp()); row.put("workspaceId", log.getWorkspaceId());
            row.put("actorId", log.getUser() != null ? log.getUser().getId() : null); row.put("action", log.getAction());
            row.put("resourceType", log.getResourceType()); row.put("resourceId", log.getResourceId()); row.put("success", log.getSuccess());
            row.put("ipAddress", log.getIpAddress()); row.put("details", log.getDetails()); row.put("previousHash", log.getPreviousHash()); row.put("entryHash", log.getEntryHash());
            return row;
        }).toList();
        try {
            if (!"csv".equalsIgnoreCase(format)) return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
            StringBuilder csv = new StringBuilder("id,timestamp,workspace_id,actor_id,action,resource_type,resource_id,success,ip_address,details,previous_hash,entry_hash\n");
            for (Map<String, Object> row : rows) csv.append(row.values().stream().map(this::csv).collect(java.util.stream.Collectors.joining(","))).append('\n');
            return csv.toString();
        } catch (Exception e) { throw new IllegalStateException("Could not export audit evidence", e); }
    }

    private String csv(Object value) { String text = value == null ? "" : String.valueOf(value); return "\"" + text.replace("\"", "\"\"") + "\""; }

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
            if (!expectedPrevious.equals(prev)) {
                broken.add(log.getId());
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
        return sha256(canonicalString(log, previousHash));
    }

    static String canonicalString(AuditLog log, String previousHash) {
        return String.join("|",
                nullToEmpty(previousHash),
                log.getUser() != null ? String.valueOf(log.getUser().getId()) : "",
                nullToEmpty(log.getAction()),
                nullToEmpty(log.getResourceType()),
                nullToEmpty(log.getResourceId()),
                String.valueOf(Boolean.TRUE.equals(log.getSuccess())),
                nullToEmpty(log.getIpAddress()),
                nullToEmpty(log.getDetails()),
                log.getWorkspaceId() != null ? String.valueOf(log.getWorkspaceId()) : "",
                // Sub-second precision does not survive the UTC round-trip through the DB exactly.
                log.getTimestamp() != null
                        ? log.getTimestamp().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
                        : ""
        );
    }

    static String sha256(String canonical) {
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

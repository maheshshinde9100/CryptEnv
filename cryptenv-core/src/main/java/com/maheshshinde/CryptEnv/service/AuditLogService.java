package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.AuditLog;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLog logEvent(User user, String action, String resourceType, String resourceId, 
                            Boolean success, HttpServletRequest request, String details) {
        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(success != null ? success : true)
                .ipAddress(getClientIpAddress(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .details(details)
                .build();
        
        return auditLogRepository.save(auditLog);
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

    public void deleteOldLogs(LocalDateTime beforeDate) {
        auditLogRepository.findByTimestampAfter(beforeDate).forEach(auditLog -> {
            auditLogRepository.delete(auditLog);
        });
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
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".toUpperCase().equals(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}

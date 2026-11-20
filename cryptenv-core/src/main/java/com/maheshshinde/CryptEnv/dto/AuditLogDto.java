package com.maheshshinde.CryptEnv.dto;

import com.maheshshinde.CryptEnv.model.AuditLog;
import com.maheshshinde.CryptEnv.model.User;

import java.time.LocalDateTime;

public record AuditLogDto(
        Long id,
        UserRef user,
        String action,
        String resourceType,
        String resourceId,
        Boolean success,
        String ipAddress,
        String userAgent,
        String details,
        Long workspaceId,
        LocalDateTime timestamp,
        String entryHash,
        String previousHash
) {

    public record UserRef(Long id, String username, String email) {
        static UserRef from(User user) {
            return user == null ? null : new UserRef(user.getId(), user.getUsername(), user.getEmail());
        }
    }

    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                UserRef.from(log.getUser()),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getSuccess(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getDetails(),
                log.getWorkspaceId(),
                log.getTimestamp(),
                log.getEntryHash(),
                log.getPreviousHash()
        );
    }
}

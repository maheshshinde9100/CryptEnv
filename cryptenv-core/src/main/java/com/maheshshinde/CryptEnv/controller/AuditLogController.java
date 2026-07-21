package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.AuditLog;
import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Audit log management APIs")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @GetMapping
    @Operation(summary = "Get audit logs with filters")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        securityService.checkPermission(Permission.AUDIT_READ);

        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AuditLog> logs = auditLogService.getFilteredAuditLogs(
            userId, action, resourceType, startDate, endDate, success, pageable
        );

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get audit logs for a specific user")
    public ResponseEntity<Page<AuditLog>> getUserAuditLogs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        securityService.checkPermission(Permission.AUDIT_READ);

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditLogService.getUserAuditLogs(userId, pageable);

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/action/{action}")
    @Operation(summary = "Get audit logs by action type")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByAction(
            @PathVariable String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        securityService.checkPermission(Permission.AUDIT_READ);

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditLogService.getAuditLogsByAction(action, pageable);

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/resource/{resourceType}/{resourceId}")
    @Operation(summary = "Get audit logs for a specific resource")
    public ResponseEntity<Page<AuditLog>> getResourceAuditLogs(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        securityService.checkPermission(Permission.AUDIT_READ);

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditLogService.getResourceAuditLogs(resourceType, resourceId, pageable);

        return ResponseEntity.ok(logs);
    }
}

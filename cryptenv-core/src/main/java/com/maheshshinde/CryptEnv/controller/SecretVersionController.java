package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.model.SecretVersion;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.SecretVersionService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/secrets/{key}/versions")
@RequiredArgsConstructor
@Tag(name = "Secret Versions", description = "Secret version management APIs")
public class SecretVersionController {

    private final SecretVersionService secretVersionService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @GetMapping
    @Operation(summary = "Get secret version history")
    public ResponseEntity<List<SecretVersion>> getSecretHistory(@PathVariable String key,
                                                                HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_READ);
        List<SecretVersion> versions = secretVersionService.getSecretHistory(key);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_HISTORY_ACCESS",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{version}")
    @Operation(summary = "Get specific secret version")
    public ResponseEntity<SecretVersion> getVersion(@PathVariable String key,
                                                   @PathVariable Integer version,
                                                   HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_READ);
        SecretVersion secretVersion = secretVersionService.getVersion(key, version)
                .orElseThrow(() -> new RuntimeException("Version not found"));
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_VERSION_ACCESS",
                "SECRET",
                key,
                true,
                request,
                "Version: " + version
        );
        return ResponseEntity.ok(secretVersion);
    }

    @PostMapping("/rollback/{version}")
    @Operation(summary = "Rollback secret to specific version")
    public ResponseEntity<SecretVersion> rollbackToVersion(@PathVariable String key,
                                                          @PathVariable Integer version,
                                                          HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        SecretVersion rolledBackVersion = secretVersionService.rollbackToVersion(
                key, 
                version, 
                securityService.getCurrentUser().getEmail()
        );
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_ROLLBACK",
                "SECRET",
                key,
                true,
                request,
                "Rolled back to version: " + version
        );
        return ResponseEntity.ok(rolledBackVersion);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active version of secret")
    public ResponseEntity<SecretVersion> getActiveVersion(@PathVariable String key,
                                                         HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_READ);
        SecretVersion activeVersion = secretVersionService.getActiveVersion(key)
                .orElseThrow(() -> new RuntimeException("No active version found"));
        return ResponseEntity.ok(activeVersion);
    }
}

package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.SecretService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/secrets/{key}/lifecycle")
@RequiredArgsConstructor
@Tag(name = "Secret Lifecycle", description = "Secret lifecycle management APIs")
public class SecretLifecycleController {

    private final SecretService secretService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @PostMapping("/soft-delete")
    @Operation(summary = "Soft delete a secret")
    public ResponseEntity<Void> softDelete(@PathVariable String key,
                                           HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_DELETE);
        secretService.softDeleteSecret(key, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_SOFT_DELETE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/restore")
    @Operation(summary = "Restore a soft-deleted secret")
    public ResponseEntity<Void> restore(@PathVariable String key,
                                       HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        secretService.restoreSecret(key, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_RESTORE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activate")
    @Operation(summary = "Activate a secret")
    public ResponseEntity<Void> activate(@PathVariable String key,
                                        HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        secretService.activateSecret(key, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_ACTIVATE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deactivate")
    @Operation(summary = "Deactivate a secret")
    public ResponseEntity<Void> deactivate(@PathVariable String key,
                                         HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        secretService.deactivateSecret(key, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_DEACTIVATE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rotation-interval")
    @Operation(summary = "Set rotation interval for a secret")
    public ResponseEntity<Secret> setRotationInterval(@PathVariable String key,
                                                      @RequestParam Integer intervalDays,
                                                      HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        Secret secret = secretService.setRotationInterval(key, intervalDays, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_ROTATION_SET",
                "SECRET",
                key,
                true,
                request,
                "Interval: " + intervalDays + " days"
        );
        return ResponseEntity.ok(secret);
    }

    @PostMapping("/auto-rotate/enable")
    @Operation(summary = "Enable automatic rotation for a secret")
    public ResponseEntity<Secret> enableAutoRotation(@PathVariable String key,
                                                   HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        Secret secret = secretService.enableAutoRotation(key, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_AUTO_ROTATE_ENABLE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.ok(secret);
    }

    @PostMapping("/auto-rotate/disable")
    @Operation(summary = "Disable automatic rotation for a secret")
    public ResponseEntity<Secret> disableAutoRotation(@PathVariable String key,
                                                    HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        Secret secret = secretService.disableAutoRotation(key, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_AUTO_ROTATE_DISABLE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.ok(secret);
    }

    @PostMapping("/expiration")
    @Operation(summary = "Set expiration date for a secret")
    public ResponseEntity<Secret> setExpiration(@PathVariable String key,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiresAt,
                                              HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        Secret secret = secretService.setExpiration(key, expiresAt, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_EXPIRATION_SET",
                "SECRET",
                key,
                true,
                request,
                "Expires at: " + expiresAt
        );
        return ResponseEntity.ok(secret);
    }

    @DeleteMapping("/expiration")
    @Operation(summary = "Remove expiration from a secret")
    public ResponseEntity<Void> removeExpiration(@PathVariable String key,
                                                HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        secretService.removeExpiration(key, securityService.getCurrentUser().getEmail());
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_EXPIRATION_REMOVE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.noContent().build();
    }
}

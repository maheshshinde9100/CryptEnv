package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.SecretService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
@Tag(name = "Secrets", description = "Secret management APIs")
public class SecretController {

    private final SecretService secretService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @PostMapping
    @Operation(summary = "Create a new secret")
    public ResponseEntity<Secret> createSecret(@Valid @RequestBody Secret secret,
                                             HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        Secret createdSecret = secretService.createSecret(secret);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_CREATE",
                "SECRET",
                createdSecret.getKey(),
                true,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSecret);
    }

    @GetMapping
    @Operation(summary = "Get all secrets")
    public ResponseEntity<List<Secret>> getAllSecrets() {
        securityService.checkPermission(Permission.SECRET_READ);
        List<Secret> secrets = secretService.getAllSecrets();
        return ResponseEntity.ok(secrets);
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get a secret by key")
    public ResponseEntity<Secret> getSecretByKey(@PathVariable String key,
                                                HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_READ);
        Secret secret = secretService.getSecretByKey(key);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_ACCESS",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.ok(secret);
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update a secret")
    public ResponseEntity<Secret> updateSecret(@PathVariable String key,
                                              @Valid @RequestBody Secret secret,
                                              HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        Secret updatedSecret = secretService.updateSecret(key, secret);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_UPDATE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.ok(updatedSecret);
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Delete a secret")
    public ResponseEntity<Void> deleteSecret(@PathVariable String key,
                                            HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_DELETE);
        secretService.deleteSecret(key);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_DELETE",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.noContent().build();
    }
}

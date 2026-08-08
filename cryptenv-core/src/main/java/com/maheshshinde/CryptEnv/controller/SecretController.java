package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.SecretCreateDto;
import com.maheshshinde.CryptEnv.dto.SecretResponseDto;
import com.maheshshinde.CryptEnv.dto.SecretUpdateDto;
import com.maheshshinde.CryptEnv.model.Permission;
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
    public ResponseEntity<SecretResponseDto> createSecret(@Valid @RequestBody SecretCreateDto createDto,
                                             HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        SecretResponseDto createdSecret = secretService.createSecret(createDto);
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
    @Operation(summary = "Get all secrets for current user")
    public ResponseEntity<List<SecretResponseDto>> getAllSecrets() {
        securityService.checkPermission(Permission.SECRET_READ);
        List<SecretResponseDto> secrets = secretService.getAllSecretsForCurrentUser();
        return ResponseEntity.ok(secrets);
    }

    @GetMapping("/environment/{environmentId}")
    @Operation(summary = "Get all secrets for an environment")
    public ResponseEntity<List<SecretResponseDto>> getSecretsByEnvironment(@PathVariable Long environmentId) {
        securityService.checkPermission(Permission.SECRET_READ);
        List<SecretResponseDto> secrets = secretService.getSecretsByEnvironment(environmentId);
        return ResponseEntity.ok(secrets);
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get a secret by key (across user's environments)")
    public ResponseEntity<SecretResponseDto> getSecretByKey(@PathVariable String key,
                                                HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_READ);
        SecretResponseDto secret = secretService.getSecretByKey(key);
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

    @GetMapping("/environment/{environmentId}/{key}")
    @Operation(summary = "Get a specific secret by environment and key")
    public ResponseEntity<SecretResponseDto> getSecretByEnvironmentAndKey(
            @PathVariable Long environmentId,
            @PathVariable String key,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_READ);
        SecretResponseDto secret = secretService.getSecretByEnvironmentAndKey(environmentId, key);
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

    @PutMapping("/environment/{environmentId}/{key}")
    @Operation(summary = "Update a secret")
    public ResponseEntity<SecretResponseDto> updateSecret(@PathVariable Long environmentId,
                                              @PathVariable String key,
                                              @Valid @RequestBody SecretUpdateDto updateDto,
                                              HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        SecretResponseDto updatedSecret = secretService.updateSecret(environmentId, key, updateDto);
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
    @Operation(summary = "Delete a secret by key")
    public ResponseEntity<Void> deleteSecret(@PathVariable String key,
                                            HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_DELETE);
        secretService.deleteSecretGlobal(key);
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

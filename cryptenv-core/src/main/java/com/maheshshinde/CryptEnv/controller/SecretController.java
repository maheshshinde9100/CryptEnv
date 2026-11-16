package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.SecretCreateDto;
import com.maheshshinde.CryptEnv.dto.SecretResponseDto;
import com.maheshshinde.CryptEnv.dto.SecretUpdateDto;
import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.MfaService;
import com.maheshshinde.CryptEnv.service.SecretImportExportService;
import com.maheshshinde.CryptEnv.service.SecretService;
import com.maheshshinde.CryptEnv.service.SecretValueGenerator;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
@Tag(name = "Secrets", description = "Secret management APIs")
public class SecretController {

    private final SecretService secretService;
    private final SecretImportExportService secretImportExportService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;
    private final MfaService mfaService;
    private final SecretValueGenerator secretValueGenerator;

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
        return noStore(ResponseEntity.status(HttpStatus.CREATED).body(createdSecret));
    }

    @GetMapping
    @Operation(summary = "Get all secrets for current user")
    public ResponseEntity<List<SecretResponseDto>> getAllSecrets() {
        securityService.checkPermission(Permission.SECRET_READ);
        List<SecretResponseDto> secrets = secretService.getAllSecretsForCurrentUser();
        return noStore(ResponseEntity.ok(secrets));
    }

    @GetMapping("/environment/{environmentId}")
    @Operation(summary = "Get all secrets for an environment")
    public ResponseEntity<List<SecretResponseDto>> getSecretsByEnvironment(@PathVariable Long environmentId) {
        securityService.checkPermission(Permission.SECRET_READ);
        List<SecretResponseDto> secrets = secretService.getSecretsByEnvironment(environmentId);
        return noStore(ResponseEntity.ok(secrets));
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
        return noStore(ResponseEntity.ok(secret));
    }

    private static <T> ResponseEntity<T> noStore(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response.getBody());
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
        return noStore(ResponseEntity.ok(secret));
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
        return noStore(ResponseEntity.ok(updatedSecret));
    }

    @PostMapping("/environment/{environmentId}/{key}/rotate")
    @Operation(summary = "Generate and atomically rotate a secret value")
    public ResponseEntity<SecretResponseDto> rotateSecret(@PathVariable Long environmentId,
                                                           @PathVariable String key,
                                                           HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        SecretResponseDto current = secretService.getSecretByEnvironmentAndKey(environmentId, key);
        SecretResponseDto rotated = secretService.rotateSecret(environmentId, key,
                secretValueGenerator.generate(com.maheshshinde.CryptEnv.model.Secret.builder()
                        .key(current.getKey())
                        .description(current.getDescription())
                        .secretType(current.getSecretType())
                        .build()));
        auditLogService.logEvent(securityService.getCurrentUser(), "SECRET_ROTATE", "SECRET", key,
                true, request, "Manual rotation");
        return noStore(ResponseEntity.ok(rotated));
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

    @DeleteMapping("/environment/{environmentId}/{key}")
    @Operation(summary = "Delete a secret from a specific environment")
    public ResponseEntity<Void> deleteSecretByEnvironment(@PathVariable Long environmentId,
                                                            @PathVariable String key,
                                                            HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_DELETE);
        secretService.deleteSecret(environmentId, key);
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

    @PostMapping("/import")
    @Operation(summary = "Import secrets from ENV, JSON, or YAML")
    public ResponseEntity<Map<String, Object>> importSecrets(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        Long environmentId = Long.valueOf(body.get("environmentId"));
        Map<String, Object> result = secretImportExportService.importSecrets(
                environmentId, body.get("format"), body.get("content"));
        auditLogService.logEvent(securityService.getCurrentUser(), "SECRET_IMPORT", "ENVIRONMENT",
                environmentId.toString(), true, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/export")
    @Operation(summary = "Export secrets as ENV, JSON, or YAML")
    public ResponseEntity<String> exportSecrets(
            @RequestParam Long environmentId,
            @RequestParam(defaultValue = "ENV") String format) {
        securityService.checkPermission(Permission.SECRET_READ);
        String body = secretImportExportService.exportSecrets(environmentId, format);
        return noStore(ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"secrets." + format.toLowerCase() + "\"")
                .body(body));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk create/update secrets")
    public ResponseEntity<Map<String, Object>> bulk(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_WRITE);
        Long environmentId = Long.valueOf(body.get("environmentId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("secrets");
        Map<String, Object> result = secretImportExportService.bulkUpsert(environmentId, items);
        auditLogService.logEvent(securityService.getCurrentUser(), "SECRET_BULK", "ENVIRONMENT",
                environmentId.toString(), true, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/environment/{environmentId}/{key}/reveal")
    @Operation(summary = "Reveal decrypted secret value (requires step-up MFA)")
    public ResponseEntity<Map<String, String>> revealSecret(
            @PathVariable Long environmentId,
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_READ);
        String stepUpToken = body.get("stepUpToken");
        String totpCode = body.get("totpCode");

        // Verify step-up MFA
        mfaService.verifyStepUpToken(stepUpToken, totpCode);

        // Get decrypted value
        String decryptedValue = secretService.getDecryptedSecretValue(environmentId, key);

        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_REVEAL",
                "SECRET",
                key,
                true,
                request
        );

        return noStore(ResponseEntity.ok(Map.of("value", decryptedValue)));
    }

    @GetMapping("/environment/{environmentId}/{key}/versions/diff")
    @Operation(summary = "Compare two secret versions")
    public ResponseEntity<Map<String, Object>> diffVersions(
            @PathVariable Long environmentId,
            @PathVariable String key,
            @RequestParam Integer fromVersion,
            @RequestParam Integer toVersion,
            HttpServletRequest request) {
        securityService.checkPermission(Permission.SECRET_READ);
        Map<String, Object> diff = secretService.diffVersions(environmentId, key, fromVersion, toVersion);
        auditLogService.logEvent(
                securityService.getCurrentUser(),
                "SECRET_VERSION_DIFF",
                "SECRET",
                key,
                true,
                request
        );
        return ResponseEntity.ok(diff);
    }
}

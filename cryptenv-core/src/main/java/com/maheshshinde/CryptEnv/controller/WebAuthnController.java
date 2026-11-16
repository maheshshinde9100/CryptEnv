package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import com.maheshshinde.CryptEnv.service.WebAuthnService;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/webauthn")
@RequiredArgsConstructor
@Tag(name = "WebAuthn", description = "WebAuthn/FIDO2 passkey authentication APIs")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;
    private final RelyingParty relyingParty;
    private final AuthenticationService authenticationService;
    private final AuditLogService auditLogService;

    @PostMapping("/register/start")
    @Operation(summary = "Start WebAuthn registration")
    public ResponseEntity<PublicKeyCredentialCreationOptions> startRegistration(HttpServletRequest request) {
        var currentUser = authenticationService.getCurrentUser();
        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(com.yubico.webauthn.data.UserIdentity.builder()
                                .name(currentUser.getUsername())
                                .displayName(currentUser.getEmail())
                                .id(com.yubico.webauthn.data.ByteArray.fromBase64(java.util.Base64.getUrlEncoder().encodeToString(currentUser.getId().toString().getBytes())))
                                .build())
                        .build()
        );
        return ResponseEntity.ok(options);
    }

    @PostMapping("/register/finish")
    @Operation(summary = "Complete WebAuthn registration")
    public ResponseEntity<Map<String, String>> finishRegistration(@RequestBody Map<String, Object> credential,
                                                                   HttpServletRequest request) {
        // TODO: Implement WebAuthn registration finish
        return ResponseEntity.ok(Map.of("message", "Passkey registration not yet implemented"));
    }

    @PostMapping("/login/start")
    @Operation(summary = "Start WebAuthn login")
    public ResponseEntity<Map<String, String>> startLogin(@RequestBody Map<String, String> body) {
        // TODO: Implement WebAuthn login start
        return ResponseEntity.ok(Map.of("message", "Passkey login not yet implemented"));
    }

    @PostMapping("/login/finish")
    @Operation(summary = "Complete WebAuthn login")
    public ResponseEntity<Map<String, String>> finishLogin(@RequestBody Map<String, Object> credential,
                                                      HttpServletRequest request) {
        // TODO: Implement WebAuthn login finish
        return ResponseEntity.ok(Map.of("message", "Passkey login not yet implemented"));
    }

    @DeleteMapping("/credentials/{credentialId}")
    @Operation(summary = "Remove a WebAuthn credential")
    public ResponseEntity<Map<String, String>> removeCredential(@PathVariable String credentialId,
                                                                  HttpServletRequest request) {
        webAuthnService.removeCredential(credentialId);
        var currentUser = authenticationService.getCurrentUser();
        auditLogService.logEvent(
                authenticationService.getUserByEmail(currentUser.getEmail()),
                "WEBAUTHN_CREDENTIAL_REMOVED",
                "USER",
                currentUser.getId().toString(),
                true,
                request
        );
        return ResponseEntity.ok(Map.of("message", "Credential removed"));
    }
}

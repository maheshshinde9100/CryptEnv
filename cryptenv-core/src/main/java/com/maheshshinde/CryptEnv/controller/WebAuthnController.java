package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import com.maheshshinde.CryptEnv.service.WebAuthnService;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.RegistrationResult;
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
                                .id(java.util.Base64.getUrlEncoder().encode(currentUser.getId().toString().getBytes()))
                                .build())
                        .build()
        );
        return ResponseEntity.ok(options);
    }

    @PostMapping("/register/finish")
    @Operation(summary = "Complete WebAuthn registration")
    public ResponseEntity<Map<String, String>> finishRegistration(@RequestBody RegistrationResult result,
                                                                   HttpServletRequest request) {
        var currentUser = authenticationService.getCurrentUser();
        relyingParty.finishRegistration(result);
        
        webAuthnService.addCredential(
                currentUser.getId(),
                result.getResponse().getAttestedCredentialData().get().getCredentialId().getBase64(),
                result.getResponse().getAttestedCredentialData().get().getCredentialPublicKey().getBase64(),
                result.getResponse().getUserHandle().getBase64(),
                result.getResponse().getAttestedCredentialData().get().getAaguid().getBase64(),
                result.getAttestationType().name(),
                "Passkey"
        );
        
        auditLogService.logEvent(
                authenticationService.getUserByEmail(currentUser.getEmail()),
                "WEBAUTHN_REGISTERED",
                "USER",
                currentUser.getId().toString(),
                true,
                request
        );
        
        return ResponseEntity.ok(Map.of("message", "Passkey registered successfully"));
    }

    @PostMapping("/login/start")
    @Operation(summary = "Start WebAuthn login")
    public ResponseEntity<PublicKeyCredentialCreationOptions> startLogin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        PublicKeyCredentialCreationOptions options = relyingParty.startLoginOptions(
                com.yubico.webauthn.StartLoginOptions.builder()
                        .username(username)
                        .build()
        );
        return ResponseEntity.ok(options);
    }

    @PostMapping("/login/finish")
    @Operation(summary = "Complete WebAuthn login")
    public ResponseEntity<JwtResponseDto> finishLogin(@RequestBody RegistrationResult result,
                                                      HttpServletRequest request) {
        relyingParty.finishLogin(result);
        String username = result.getResponse().getUserHandle().getBase64();
        var user = authenticationService.getUserService().findByUsername(username);
        JwtResponseDto jwtResponse = authenticationService.generateTokens(user);
        
        auditLogService.logEvent(
                authenticationService.getUserByEmail(user.getEmail()),
                "WEBAUTHN_LOGIN",
                "USER",
                user.getId().toString(),
                true,
                request
        );
        
        return ResponseEntity.ok(jwtResponse);
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

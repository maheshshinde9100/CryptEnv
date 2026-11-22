package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.OAuthConnection;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import com.maheshshinde.CryptEnv.service.GoogleOAuthService;
import com.maheshshinde.CryptEnv.service.OAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth", description = "OAuth2 login (Google) APIs")
public class OAuthController {

    private final GoogleOAuthService googleOAuthService;
    private final OAuthService oauthService;
    private final AuthenticationService authenticationService;
    private final AuditLogService auditLogService;

    @GetMapping("/google/status")
    @Operation(summary = "Whether Google sign-in is configured on this deployment")
    public ResponseEntity<Map<String, Boolean>> googleStatus() {
        return ResponseEntity.ok(Map.of("configured", googleOAuthService.isConfigured()));
    }

    @GetMapping("/google/authorize")
    @Operation(summary = "Build the Google consent URL and signed state")
    public ResponseEntity<Map<String, String>> googleAuthorize(
            @RequestParam(required = false) String origin,
            HttpServletRequest request) {
        if (!googleOAuthService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not configured on this server");
        }
        String redirectUri = googleOAuthService.redirectUri(callbackUri(request));
        return ResponseEntity.ok(Map.of(
                "authorizationUrl",
                googleOAuthService.authorizationUrl(origin, redirectUri)));
    }

    @GetMapping("/google/callback")
    @Operation(summary = "Handle the Google redirect, issue CryptEnv tokens, and return to the dashboard")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {
        GoogleOAuthService.LoginResult result = googleOAuthService.callback(
                code, state, error, googleOAuthService.redirectUri(callbackUri(request)));
        if (result.user() != null) {
            auditLogService.logEvent(result.user(), "OAUTH_LOGIN", "USER",
                    result.user().getId().toString(), true, request);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.redirectUrl())
                .build();
    }

    @GetMapping("/connections")
    @Operation(summary = "List the current user's linked OAuth providers (no tokens returned)")
    public ResponseEntity<List<Map<String, Object>>> getConnections() {
        var currentUser = authenticationService.getCurrentUser();
        List<Map<String, Object>> connections = oauthService.getUserConnections(currentUser.getId())
                .stream()
                .map(this::toSafeConnection)
                .toList();
        return ResponseEntity.ok(connections);
    }

    @DeleteMapping("/connections/{provider}")
    @Operation(summary = "Unlink an OAuth provider")
    public ResponseEntity<Map<String, String>> unlinkProvider(@PathVariable String provider,
                                                              HttpServletRequest request) {
        var currentUser = authenticationService.getCurrentUser();
        oauthService.unlinkOAuth(currentUser.getId(), provider);

        auditLogService.logEvent(
                authenticationService.getUserByEmail(currentUser.getEmail()),
                "OAUTH_UNLINKED",
                "USER",
                currentUser.getId().toString(),
                true,
                request
        );

        return ResponseEntity.ok(Map.of("message", "OAuth provider unlinked"));
    }

    private String callbackUri(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromContextPath(request)
                .path("/api/auth/oauth/google/callback")
                .build()
                .toUriString();
    }

    private Map<String, Object> toSafeConnection(OAuthConnection connection) {
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("provider", connection.getProvider());
        safe.put("email", connection.getEmail());
        safe.put("name", connection.getName());
        safe.put("avatarUrl", connection.getAvatarUrl());
        safe.put("createdAt", connection.getCreatedAt());
        return safe;
    }
}
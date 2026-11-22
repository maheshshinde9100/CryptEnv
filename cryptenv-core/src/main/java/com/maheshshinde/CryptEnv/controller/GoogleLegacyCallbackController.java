package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Serves {@code /login/oauth2/code/google}, the redirect URI Google often already has
 * registered. Without this mapping Spring Security answers that path with a bare 400.
 */
@RestController
@RequiredArgsConstructor
public class GoogleLegacyCallbackController {

    private final GoogleOAuthService googleOAuthService;
    private final AuditLogService auditLogService;

    @GetMapping("/login/oauth2/code/google")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {
        String derived = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/login/oauth2/code/google")
                .build()
                .toUriString();
        GoogleOAuthService.LoginResult result = googleOAuthService.callback(
                code, state, error, googleOAuthService.redirectUri(derived));
        if (result.user() != null) {
            auditLogService.logEvent(result.user(), "OAUTH_LOGIN", "USER",
                    result.user().getId().toString(), true, request);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.redirectUrl())
                .build();
    }
}

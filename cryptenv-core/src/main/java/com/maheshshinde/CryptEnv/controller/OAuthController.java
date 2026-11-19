package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.dto.UserResponseDto;
import com.maheshshinde.CryptEnv.model.OAuthConnection;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import com.maheshshinde.CryptEnv.service.OAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth", description = "OAuth2 login (GitHub/Google) APIs")
public class OAuthController {

    private final OAuthService oauthService;
    private final AuthenticationService authenticationService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    @PostMapping("/callback/{provider}")
    @Operation(summary = "Handle OAuth callback from provider")
    public ResponseEntity<JwtResponseDto> handleCallback(@PathVariable String provider,
                                                         @RequestBody Map<String, Object> payload,
                                                         HttpServletRequest request) {
        String providerUserId = (String) payload.get("providerUserId");
        String accessToken = (String) payload.get("accessToken");
        String refreshToken = (String) payload.get("refreshToken");
        String email = (String) payload.get("email");
        String name = (String) payload.get("name");
        String avatarUrl = (String) payload.get("avatarUrl");
        
        User user = oauthService.findUserByOAuth(provider, providerUserId)
                .orElseGet(() -> {
                    UserResponseDto newUser = authenticationService.register(
                            new com.maheshshinde.CryptEnv.dto.UserRegistrationDto(
                                    email,
                                    email.split("@")[0],
                                    null,
                                    name,
                                    null
                            )
                    );
                    oauthService.linkOAuth(newUser.getId(), provider, providerUserId, 
                            accessToken, refreshToken, null, email, name, avatarUrl);
                    return userRepository.findByEmail(newUser.getEmail()).orElseThrow();
                });
        
        JwtResponseDto jwtResponse = authenticationService.authenticate(
                new com.maheshshinde.CryptEnv.dto.UserLoginDto(email, null)
        );
        
        auditLogService.logEvent(
                authenticationService.getUserByEmail(user.getEmail()),
                "OAUTH_LOGIN",
                "USER",
                user.getId().toString(),
                true,
                request
        );
        
        return ResponseEntity.ok(jwtResponse);
    }

    @GetMapping("/connections")
    @Operation(summary = "Get user's OAuth connections")
    public ResponseEntity<List<OAuthConnection>> getConnections() {
        var currentUser = authenticationService.getCurrentUser();
        return ResponseEntity.ok(oauthService.getUserConnections(currentUser.getId()));
    }

    @DeleteMapping("/connections/{provider}")
    @Operation(summary = "Unlink OAuth provider")
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
}

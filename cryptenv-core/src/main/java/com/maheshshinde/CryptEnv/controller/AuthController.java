package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.dto.UserLoginDto;
import com.maheshshinde.CryptEnv.dto.UserRegistrationDto;
import com.maheshshinde.CryptEnv.dto.UserResponseDto;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AuditLogService auditLogService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody UserRegistrationDto registrationDto,
                                                   HttpServletRequest request) {
        UserResponseDto userResponse = authenticationService.register(registrationDto);
        auditLogService.logEvent(
                authenticationService.getUserByEmail(registrationDto.getEmail()),
                "USER_REGISTER",
                "USER",
                userResponse.getId().toString(),
                true,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT token")
    public ResponseEntity<JwtResponseDto> authenticate(@Valid @RequestBody UserLoginDto loginDto,
                                                       HttpServletRequest request) {
        try {
            JwtResponseDto jwtResponse = authenticationService.authenticate(loginDto);
            auditLogService.logEvent(
                    authenticationService.getUserByEmail(loginDto.getEmail()),
                    "LOGIN",
                    "USER",
                    jwtResponse.getUserId().toString(),
                    true,
                    request
            );
            return ResponseEntity.ok(jwtResponse);
        } catch (Exception e) {
            try {
                auditLogService.logEvent(
                        authenticationService.getUserByEmail(loginDto.getEmail()),
                        "FAILED_LOGIN",
                        "USER",
                        null,
                        false,
                        request,
                        e.getMessage()
                );
            } catch (Exception logError) {
                // If user doesn't exist, skip audit log for failed login
            }
            throw e;
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a new access token")
    public ResponseEntity<JwtResponseDto> refresh(@RequestBody java.util.Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(authenticationService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user — revokes current access token; optional sign-out everywhere")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean everywhere) {
        String bearer = request.getHeader("Authorization");
        String accessToken = null;
        if (bearer != null && bearer.startsWith("Bearer ")) {
            accessToken = bearer.substring(7);
        }
        try {
            var user = authenticationService.getCurrentUser();
            authenticationService.logout(accessToken, everywhere);
            auditLogService.logEvent(
                    authenticationService.getUserByEmail(user.getEmail()),
                    everywhere ? "LOGOUT_EVERYWHERE" : "LOGOUT",
                    "USER",
                    user.getId().toString(),
                    true,
                    request
            );
        } catch (Exception e) {
            authenticationService.logout(accessToken, everywhere);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        UserResponseDto userResponse = authenticationService.getCurrentUser();
        return ResponseEntity.ok(userResponse);
    }

    @PostMapping("/api-key/regenerate")
    @Operation(summary = "Regenerate user API Key")
    public ResponseEntity<UserResponseDto> regenerateApiKey() {
        var currentUser = authenticationService.getCurrentUser();
        UserResponseDto updated = authenticationService.getUserService().generateNewApiKey(currentUser.getId());
        return ResponseEntity.ok(updated);
    }
}

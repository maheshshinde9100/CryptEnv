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
                    jwtResponse.getUser().getId().toString(),
                    true,
                    request
            );
            return ResponseEntity.ok(jwtResponse);
        } catch (Exception e) {
            auditLogService.logEvent(
                    authenticationService.getUserByEmail(loginDto.getEmail()),
                    "FAILED_LOGIN",
                    "USER",
                    null,
                    false,
                    request,
                    e.getMessage()
            );
            throw e;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        try {
            var user = authenticationService.getCurrentUser();
            auditLogService.logEvent(
                    authenticationService.getUserByEmail(user.getEmail()),
                    "LOGOUT",
                    "USER",
                    user.getId().toString(),
                    true,
                    request
            );
        } catch (Exception e) {
            // Log logout even if user is not found
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        UserResponseDto userResponse = authenticationService.getCurrentUser();
        return ResponseEntity.ok(userResponse);
    }
}

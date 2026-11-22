package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.ForgotPasswordDto;
import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.dto.MfaCodeDto;
import com.maheshshinde.CryptEnv.dto.MfaDisableDto;
import com.maheshshinde.CryptEnv.dto.MfaEnableResponseDto;
import com.maheshshinde.CryptEnv.dto.MfaSetupResponseDto;
import com.maheshshinde.CryptEnv.dto.MfaVerifyDto;
import com.maheshshinde.CryptEnv.dto.ResetPasswordDto;
import com.maheshshinde.CryptEnv.dto.UserLoginDto;
import com.maheshshinde.CryptEnv.dto.UserRegistrationDto;
import com.maheshshinde.CryptEnv.dto.UserResponseDto;
import com.maheshshinde.CryptEnv.dto.VerifyResetCodeDto;
import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import com.maheshshinde.CryptEnv.service.EmailAddressService;
import com.maheshshinde.CryptEnv.service.MfaService;
import com.maheshshinde.CryptEnv.service.PasswordResetService;
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
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;
    private final EmailAddressService emailAddressService;

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

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset token (always returns a generic success message)")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordDto dto,
            HttpServletRequest request) {
        var response = passwordResetService.forgotPassword(dto);
        try {
            auditLogService.logEvent(
                    authenticationService.getUserByEmail(dto.getEmail()),
                    "PASSWORD_RESET_REQUESTED",
                    "USER",
                    null,
                    true,
                    request
            );
        } catch (Exception ignored) {
            // Avoid leaking whether the email exists via audit failures
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-reset-code")
    @Operation(summary = "Verify the emailed reset code and obtain a one-time reset token")
    public ResponseEntity<java.util.Map<String, String>> verifyResetCode(
            @Valid @RequestBody VerifyResetCodeDto dto) {
        return ResponseEntity.ok(passwordResetService.verifyCode(dto));
    }

    @GetMapping("/email-check")
    @Operation(summary = "Check email syntax, typos, and whether the domain accepts mail")
    public ResponseEntity<EmailAddressService.Result> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(emailAddressService.check(email));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a one-time reset token")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordDto dto,
            HttpServletRequest request) {
        var response = passwordResetService.resetPassword(dto);
        auditLogService.logEvent(
                null,
                "PASSWORD_RESET_COMPLETED",
                "USER",
                null,
                true,
                request
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/setup")
    @Operation(summary = "Begin TOTP MFA setup — returns Base32 secret and otpauth URI")
    public ResponseEntity<MfaSetupResponseDto> mfaSetup(HttpServletRequest request) {
        MfaSetupResponseDto response = mfaService.setup();
        auditLogService.logEvent(
                authenticationService.getUserByEmail(authenticationService.getCurrentUser().getEmail()),
                "MFA_SETUP",
                "USER",
                null,
                true,
                request
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Confirm TOTP and enable MFA; returns one-time recovery codes")
    public ResponseEntity<MfaEnableResponseDto> mfaEnable(@Valid @RequestBody MfaCodeDto dto,
                                                          HttpServletRequest request) {
        MfaEnableResponseDto response = mfaService.enable(dto.getCode());
        auditLogService.logEvent(
                authenticationService.getUserByEmail(authenticationService.getCurrentUser().getEmail()),
                "MFA_ENABLED",
                "USER",
                null,
                true,
                request
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "Disable MFA (requires password + TOTP or recovery code)")
    public ResponseEntity<java.util.Map<String, String>> mfaDisable(@Valid @RequestBody MfaDisableDto dto,
                                                                    HttpServletRequest request) {
        mfaService.disable(dto);
        auditLogService.logEvent(
                authenticationService.getUserByEmail(authenticationService.getCurrentUser().getEmail()),
                "MFA_DISABLED",
                "USER",
                null,
                true,
                request
        );
        return ResponseEntity.ok(java.util.Map.of("message", "MFA disabled"));
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Complete login after MFA challenge with TOTP or recovery code")
    public ResponseEntity<JwtResponseDto> mfaVerify(@Valid @RequestBody MfaVerifyDto dto,
                                                    HttpServletRequest request) {
        JwtResponseDto jwtResponse = mfaService.verifyLogin(dto);
        auditLogService.logEvent(
                authenticationService.getUserByEmail(jwtResponse.getEmail()),
                "MFA_LOGIN_VERIFIED",
                "USER",
                jwtResponse.getUserId().toString(),
                true,
                request
        );
        return ResponseEntity.ok(jwtResponse);
    }

    @PostMapping("/mfa/step-up")
    @Operation(summary = "Generate step-up MFA challenge token for sensitive operations")
    public ResponseEntity<java.util.Map<String, String>> generateStepUpToken(HttpServletRequest request) {
        String stepUpToken = mfaService.generateStepUpToken();
        auditLogService.logEvent(
                authenticationService.getUserByEmail(authenticationService.getCurrentUser().getEmail()),
                "STEP_UP_MFA_REQUESTED",
                "USER",
                null,
                true,
                request
        );
        return ResponseEntity.ok(java.util.Map.of("stepUpToken", stepUpToken));
    }

    @PostMapping("/mfa/step-up/verify")
    @Operation(summary = "Verify step-up MFA challenge with TOTP code")
    public ResponseEntity<java.util.Map<String, String>> verifyStepUpToken(
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        String stepUpToken = body.get("stepUpToken");
        String totpCode = body.get("totpCode");
        mfaService.verifyStepUpToken(stepUpToken, totpCode);
        auditLogService.logEvent(
                authenticationService.getUserByEmail(authenticationService.getCurrentUser().getEmail()),
                "STEP_UP_MFA_VERIFIED",
                "USER",
                null,
                true,
                request
        );
        return ResponseEntity.ok(java.util.Map.of("message", "Step-up MFA verified"));
    }
}

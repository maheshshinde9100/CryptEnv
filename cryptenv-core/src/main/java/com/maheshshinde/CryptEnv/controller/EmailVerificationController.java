package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.EmailVerificationService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public registration-email verification endpoints. Responses never disclose account existence. */
@RestController
@RequestMapping("/api/auth/email-verification")
@RequiredArgsConstructor
@Validated
public class EmailVerificationController {
    private final EmailVerificationService verificationService;

    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestBody VerifyRequest request) {
        verificationService.verify(request.email(), request.code());
        return ResponseEntity.ok(Map.of("message", "Email verified. You can now sign in."));
    }

    @PostMapping("/resend")
    public ResponseEntity<Map<String, String>> resend(@RequestBody EmailRequest request) {
        verificationService.resend(request.email());
        return ResponseEntity.ok(Map.of("message", "If an account needs verification, a code has been sent."));
    }

    /** Lets the sign-up UI warn when this deployment cannot send verification mail. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("mailConfigured", verificationService.isMailConfigured()));
    }

    public record EmailRequest(@NotBlank @Email String email) {}
    public record VerifyRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
}

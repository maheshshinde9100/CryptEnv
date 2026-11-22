package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.ForgotPasswordDto;
import com.maheshshinde.CryptEnv.dto.ResetPasswordDto;
import com.maheshshinde.CryptEnv.dto.VerifyResetCodeDto;
import com.maheshshinde.CryptEnv.exception.InvalidCredentialsException;
import com.maheshshinde.CryptEnv.model.PasswordResetToken;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.PasswordResetTokenRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.security.ApiKeyHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

/**
 * Password recovery in two steps: a six-digit email code, then a one-time reset token.
 * The code and the token are stored only as SHA-256 hashes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final int CODE_TTL_MINUTES = 10;
    private static final int RESET_TTL_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountLockoutService accountLockoutService;
    private final TokenService tokenService;
    private final EmailSender emailSender;
    private final EmailAddressService emailAddressService;

    /**
     * Always returns the same client-facing message to avoid account enumeration.
     * When the account exists, a fresh six-digit code is emailed and any older code is invalidated.
     */
    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordDto dto) {
        String message = "If an account exists for that email, a password reset code has been sent.";
        String email = EmailAddressService.normalize(dto.getEmail());
        if (email == null || !emailAddressService.check(email).valid()) {
            return Map.of("message", message);
        }
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetTokenRepository.invalidateAllForUser(user);
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            PasswordResetToken entity = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(hash(code))
                    .expiresAt(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES))
                    .used(false)
                    .attempts(0)
                    .build();
            passwordResetTokenRepository.save(entity);
            boolean sent = emailSender.send(
                    user.getEmail(),
                    "Your CryptEnv password reset code",
                    "Your CryptEnv password reset code is " + code + ". It expires in "
                            + CODE_TTL_MINUTES + " minutes. If you did not request this, ignore this email.");
            if (!sent) {
                log.warn("Password reset code for userId={} was stored but could not be delivered", user.getId());
            }
        });
        return Map.of("message", message);
    }

    /** Validates the emailed code and returns a one-time token for the reset step. */
    @Transactional
    public Map<String, String> verifyCode(VerifyResetCodeDto dto) {
        String email = EmailAddressService.normalize(dto.getEmail());
        User user = email == null ? null : userRepository.findByEmail(email).orElse(null);
        PasswordResetToken token = user == null ? null
                : passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByIdDesc(user).orElse(null);
        if (token == null || token.getExpiresAt().isBefore(LocalDateTime.now())
                || token.getAttempts() >= MAX_ATTEMPTS) {
            throw new InvalidCredentialsException("Invalid or expired reset code");
        }
        if (!MessageDigest.isEqual(
                token.getTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(dto.getCode()).getBytes(StandardCharsets.UTF_8))) {
            token.setAttempts(token.getAttempts() + 1);
            passwordResetTokenRepository.save(token);
            throw new InvalidCredentialsException("Invalid or expired reset code");
        }

        String resetToken = generateRawToken();
        token.setTokenHash(hash(resetToken));
        token.setAttempts(0);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TTL_MINUTES));
        passwordResetTokenRepository.save(token);
        return Map.of(
                "message", "Code verified. Choose a new password.",
                "resetToken", resetToken);
    }

    @Transactional
    public Map<String, String> resetPassword(ResetPasswordDto dto) {
        String hash = hash(dto.getToken());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashAndUsedFalse(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired reset token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            passwordResetTokenRepository.save(token);
            throw new InvalidCredentialsException("Invalid or expired reset token");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setEnabled(true);
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.invalidateAllForUser(user);

        accountLockoutService.clearLockout(user.getId());
        tokenService.revokeAllSessions(user.getId());

        return Map.of("message", "Password has been reset successfully.");
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
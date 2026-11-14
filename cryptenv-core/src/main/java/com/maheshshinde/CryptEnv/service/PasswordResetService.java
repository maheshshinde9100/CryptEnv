package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.ForgotPasswordDto;
import com.maheshshinde.CryptEnv.dto.ResetPasswordDto;
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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_TTL_HOURS = 1L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountLockoutService accountLockoutService;
    private final TokenService tokenService;

    /**
     * Always returns the same client-facing message. When the email exists, a hashed token
     * is stored and the raw token is logged (email delivery not wired yet).
     */
    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordDto dto) {
        String message = "If an account exists for that email, a password reset link has been sent.";
        userRepository.findByEmail(dto.getEmail()).ifPresent(user -> {
            passwordResetTokenRepository.invalidateAllForUser(user);
            String rawToken = generateRawToken();
            PasswordResetToken entity = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(ApiKeyHasher.sha256Hex(rawToken))
                    .expiresAt(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(entity);
            log.info("Password reset token created for userId={} (raw token logged until email is wired): {}",
                    user.getId(), rawToken);
        });
        return Map.of("message", message);
    }

    @Transactional
    public Map<String, String> resetPassword(ResetPasswordDto dto) {
        String hash = ApiKeyHasher.sha256Hex(dto.getToken());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashAndUsedFalse(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired reset token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            passwordResetTokenRepository.save(token);
            throw new InvalidCredentialsException("Invalid or expired reset token");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
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
}

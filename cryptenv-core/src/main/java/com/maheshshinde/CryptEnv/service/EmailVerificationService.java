package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.EmailVerificationToken;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.EmailVerificationTokenRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/** Registration verification with a one-time, hashed six-digit code and resend-safe upsert. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {
    private static final int MAX_ATTEMPTS = 5;
    private static final int TTL_MINUTES = 10;
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void issue(User user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) return;
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailVerificationToken token = tokenRepository.findByUserId(user.getId())
                .orElseGet(() -> EmailVerificationToken.builder().user(user).build());
        token.setCodeHash(hash(code));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        token.setAttempts(0);
        tokenRepository.save(token);
        send(user.getEmail(), code);
    }

    /** Always succeeds to avoid account enumeration. */
    @Transactional
    public void resend(String email) {
        userRepository.findByEmail(email).ifPresent(this::issue);
    }

    @Transactional
    public void verify(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));
        if (Boolean.TRUE.equals(user.getEmailVerified())) return;
        EmailVerificationToken token = tokenRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));
        if (token.getExpiresAt().isBefore(LocalDateTime.now()) || token.getAttempts() >= MAX_ATTEMPTS) {
            tokenRepository.delete(token);
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        if (!MessageDigest.isEqual(token.getCodeHash().getBytes(StandardCharsets.UTF_8), hash(code).getBytes(StandardCharsets.UTF_8))) {
            token.setAttempts(token.getAttempts() + 1);
            tokenRepository.save(token);
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        user.setEmailVerified(true);
        user.setEnabled(true);
        userRepository.save(user);
        tokenRepository.delete(token);
    }

    /** True when SMTP or an HTTPS mail provider is configured. */
    public boolean isMailConfigured() {
        return emailSender.isConfigured();
    }

    private void send(String email, String code) {
        boolean sent = emailSender.send(
                email,
                "Your CryptEnv verification code",
                "Your CryptEnv verification code is " + code + ". It expires in " + TTL_MINUTES + " minutes.");
        if (!sent) {
            log.warn("Verification code for {} was stored but could not be delivered", email);
        }
    }

    private String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
}

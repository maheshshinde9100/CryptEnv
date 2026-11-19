package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.MfaDisableDto;
import com.maheshshinde.CryptEnv.dto.MfaEnableResponseDto;
import com.maheshshinde.CryptEnv.dto.MfaSetupResponseDto;
import com.maheshshinde.CryptEnv.dto.MfaVerifyDto;
import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.exception.InvalidCredentialsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.security.ApiKeyHasher;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import com.maheshshinde.CryptEnv.security.JwtTokenProvider;
import com.maheshshinde.CryptEnv.security.TotpService;
import com.maheshshinde.CryptEnv.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MfaService {

    private static final String ISSUER = "CryptEnv";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;

    @Transactional
    public MfaSetupResponseDto setup() {
        User user = requireCurrentUser();
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new InvalidCredentialsException("MFA is already enabled");
        }
        String secret = TotpService.generateSecret();
        user.setMfaSecret(encryptionService.encrypt(secret));
        user.setMfaEnabled(false);
        user.setMfaRecoveryCodes(null);
        userRepository.save(user);
        return MfaSetupResponseDto.builder()
                .secret(secret)
                .otpAuthUri(TotpService.otpAuthUri(ISSUER, user.getEmail(), secret))
                .build();
    }

    @Transactional
    public MfaEnableResponseDto enable(String code) {
        User user = requireCurrentUser();
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new InvalidCredentialsException("MFA is already enabled");
        }
        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            throw new InvalidCredentialsException("Call /api/auth/mfa/setup first");
        }
        String secret = encryptionService.decrypt(user.getMfaSecret());
        if (!TotpService.verify(secret, code)) {
            throw new InvalidCredentialsException("Invalid MFA code");
        }

        List<String> rawCodes = generateRecoveryCodes();
        user.setMfaRecoveryCodes(rawCodes.stream()
                .map(c -> ApiKeyHasher.sha256Hex(c.toUpperCase()))
                .collect(Collectors.joining(",")));
        user.setMfaEnabled(true);
        userRepository.save(user);

        return MfaEnableResponseDto.builder()
                .enabled(true)
                .recoveryCodes(rawCodes)
                .build();
    }

    @Transactional
    public void disable(MfaDisableDto dto) {
        User user = requireCurrentUser();
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new InvalidCredentialsException("MFA is not enabled");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
        }
        if (!consumeMfaOrRecoveryCode(user, dto.getCode())) {
            throw new InvalidCredentialsException("Invalid MFA code");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaRecoveryCodes(null);
        userRepository.save(user);
    }

    @Transactional
    public JwtResponseDto verifyLogin(MfaVerifyDto dto) {
        if (!tokenProvider.validateToken(dto.getMfaToken()) || !tokenProvider.isMfaChallengeToken(dto.getMfaToken())) {
            throw new InvalidCredentialsException("Invalid or expired MFA challenge token");
        }
        Long userId = tokenProvider.getUserIdFromToken(dto.getMfaToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new InvalidCredentialsException("MFA is not enabled for this account");
        }
        if (!consumeMfaOrRecoveryCode(user, dto.getCode())) {
            throw new InvalidCredentialsException("Invalid MFA code");
        }

        TokenService.TokenPair pair = tokenService.issueTokenPair(user);
        return JwtResponseDto.builder()
                .token(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(tokenProvider.getAccessTokenTtlMs() / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .mfaRequired(false)
                .build();
    }

    private boolean consumeMfaOrRecoveryCode(User user, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String trimmed = code.trim().replace(" ", "");
        if (trimmed.matches("\\d{6}") && user.getMfaSecret() != null) {
            String secret = encryptionService.decrypt(user.getMfaSecret());
            if (TotpService.verify(secret, trimmed)) {
                return true;
            }
        }
        return consumeRecoveryCode(user, trimmed);
    }

    private boolean consumeRecoveryCode(User user, String rawCode) {
        if (user.getMfaRecoveryCodes() == null || user.getMfaRecoveryCodes().isBlank()) {
            return false;
        }
        String hash = ApiKeyHasher.sha256Hex(rawCode.toUpperCase());
        List<String> hashes = new ArrayList<>(Arrays.asList(user.getMfaRecoveryCodes().split(",")));
        boolean removed = hashes.remove(hash);
        if (!removed) {
            // also try as-entered (case-sensitive legacy)
            removed = hashes.remove(ApiKeyHasher.sha256Hex(rawCode));
        }
        if (removed) {
            user.setMfaRecoveryCodes(String.join(",", hashes));
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(randomRecoveryCode());
        }
        return codes;
    }

    private static String randomRecoveryCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("User not authenticated");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Generate a step-up MFA challenge token for sensitive operations.
     * Returns a 5-minute token that must be verified with TOTP before proceeding.
     */
    @Transactional
    public String generateStepUpToken() {
        User user = requireCurrentUser();
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new InvalidCredentialsException("MFA must be enabled for step-up authentication");
        }
        return tokenProvider.generateMfaChallengeToken(user.getId(), user.getEmail());
    }

    /**
     * Verify a step-up MFA challenge token with TOTP code.
     * Returns the user if verification succeeds.
     */
    @Transactional
    public User verifyStepUpToken(String stepUpToken, String totpCode) {
        if (!tokenProvider.validateToken(stepUpToken) || !tokenProvider.isMfaChallengeToken(stepUpToken)) {
            throw new InvalidCredentialsException("Invalid or expired step-up token");
        }
        Long userId = tokenProvider.getUserIdFromToken(stepUpToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new InvalidCredentialsException("MFA not enabled for this account");
        }
        if (!consumeMfaOrRecoveryCode(user, totpCode)) {
            throw new InvalidCredentialsException("Invalid TOTP code");
        }
        return user;
    }
}

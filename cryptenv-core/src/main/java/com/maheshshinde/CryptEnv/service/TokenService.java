package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.InvalidCredentialsException;
import com.maheshshinde.CryptEnv.model.RefreshToken;
import com.maheshshinde.CryptEnv.model.RevokedAccessToken;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.RefreshTokenRepository;
import com.maheshshinde.CryptEnv.repository.RevokedAccessTokenRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.security.ApiKeyHasher;
import com.maheshshinde.CryptEnv.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    public record TokenPair(String accessToken, String refreshToken) {}

    @Transactional
    public TokenPair issueTokenPair(User user) {
        String access = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getEmail());
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        String familyId = UUID.randomUUID().toString();

        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .tokenHash(ApiKeyHasher.sha256Hex(rawRefresh))
                .familyId(familyId)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(stored);
        return new TokenPair(access, rawRefresh);
    }

    /**
     * Rotates refresh token. Reuse of an already-rotated token revokes the whole family.
     */
    @Transactional
    public TokenPair rotateRefreshToken(String rawRefreshToken) {
        String hash = ApiKeyHasher.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (Boolean.TRUE.equals(existing.getRevoked())) {
            refreshTokenRepository.revokeFamily(existing.getFamilyId());
            throw new InvalidCredentialsException("Refresh token reuse detected; session family revoked");
        }
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new InvalidCredentialsException("Refresh token expired");
        }

        existing.setRevoked(true);
        existing.setLastUsedAt(LocalDateTime.now());

        String newRaw = UUID.randomUUID() + "." + UUID.randomUUID();
        String newHash = ApiKeyHasher.sha256Hex(newRaw);
        existing.setReplacedByHash(newHash);
        refreshTokenRepository.save(existing);

        RefreshToken next = RefreshToken.builder()
                .user(existing.getUser())
                .tokenHash(newHash)
                .familyId(existing.getFamilyId())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(next);

        User user = existing.getUser();
        String access = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getEmail());
        return new TokenPair(access, newRaw);
    }

    @Transactional
    public void revokeAccessToken(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            return;
        }
        String jti = jwtTokenProvider.getJtiFromToken(accessToken);
        if (jti == null) {
            return;
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
        LocalDateTime expires = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(jwtTokenProvider.getExpirationFromToken(accessToken).getTime()),
                ZoneId.systemDefault());
        revokedAccessTokenRepository.save(RevokedAccessToken.builder()
                .jti(jti)
                .userId(userId)
                .expiresAt(expires)
                .revokedAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public boolean isAccessTokenRevoked(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            return true;
        }
        String jti = jwtTokenProvider.getJtiFromToken(accessToken);
        return jti != null && revokedAccessTokenRepository.existsByJti(jti);
    }

    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
    }
}

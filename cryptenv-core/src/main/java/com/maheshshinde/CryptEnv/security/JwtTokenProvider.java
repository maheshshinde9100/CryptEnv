package com.maheshshinde.CryptEnv.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Access token TTL — default 15 minutes. */
    @Value("${jwt.expiration:900000}")
    private long jwtExpirationMs;

    /** MFA challenge token TTL — default 5 minutes. */
    @Value("${jwt.mfa-challenge-expiration-ms:300000}")
    private long mfaChallengeExpirationMs;

    public static final String PURPOSE_MFA = "mfa";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return generateAccessToken(userPrincipal.getId(), userPrincipal.getUsername(), userPrincipal.getEmail());
    }

    public String generateAccessToken(Long userId, String username, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .id(jti)
                .subject(Long.toString(userId))
                .claim("username", username)
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateMfaChallengeToken(Long userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + mfaChallengeExpirationMs);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(Long.toString(userId))
                .claim("email", email)
                .claim("purpose", PURPOSE_MFA)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isMfaChallengeToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return PURPOSE_MFA.equals(claims.get("purpose", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    public Date getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenTtlMs() {
        return jwtExpirationMs;
    }
}

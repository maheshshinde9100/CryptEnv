package com.maheshshinde.CryptEnv.security;

import com.maheshshinde.CryptEnv.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtTokenProvider — JWT signing + claims round-trip")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    private static final String SECRET_256_HEX =
            "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET_256_HEX);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 3600_000L);
    }

    private UserPrincipal buildPrincipal(Long id, String username, String email) {
        User user = User.builder()
                .id(id)
                .username(username)
                .email(email)
                .password("encoded-password-irrelevant")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .ownedWorkspaces(new HashSet<>())
                .memberWorkspaces(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .build();
        return UserPrincipal.create(user);
    }

    @Test
    @DisplayName("generated token is non-empty and differs between users")
    void generateTokenProducesValidJwt() {
        org.springframework.security.core.Authentication authA =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        buildPrincipal(1L, "alice", "alice@example.com"),
                        null, Collections.emptyList());
        org.springframework.security.core.Authentication authB =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        buildPrincipal(2L, "bob", "bob@example.com"),
                        null, Collections.emptyList());

        String tokenA = provider.generateToken(authA);
        String tokenB = provider.generateToken(authB);
        assertNotNull(tokenA);
        assertTrue(tokenA.length() > 30);
        assertNotEquals(tokenA, tokenB);
    }

    @Test
    @DisplayName("getUserIdFromToken returns the subject ID we signed")
    void extractUserIdRoundTrip() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                buildPrincipal(42L, "u", "u@x"), null, Collections.emptyList());
        String tok = provider.generateToken(auth);
        assertEquals(42L, provider.getUserIdFromToken(tok));
    }

    @Test
    @DisplayName("validateToken returns true for valid tokens")
    void validateTokenValid() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                buildPrincipal(7L, "u", "u@x"), null, Collections.emptyList());
        assertTrue(provider.validateToken(provider.generateToken(auth)));
    }

    @Test
    @DisplayName("validateToken returns false for garbage / tampered / empty")
    void validateTokenInvalid() {
        assertFalse(provider.validateToken(""));
        assertFalse(provider.validateToken("not.a.jwt"));
        assertFalse(provider.validateToken("eyJhbGciOiJIUzI1NiJ9.e30.tampered"));
    }

    @Test
    @DisplayName("provider with different secret rejects tokens from another secret")
    void crossSecretValidationFails() {
        JwtTokenProvider other = new JwtTokenProvider();
        ReflectionTestUtils.setField(other, "jwtSecret",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        ReflectionTestUtils.setField(other, "jwtExpirationMs", 3600_000L);

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                buildPrincipal(1L, "u", "u@x"), null, Collections.emptyList());
        String signedByProvider = provider.generateToken(auth);
        assertFalse(other.validateToken(signedByProvider));
    }
}

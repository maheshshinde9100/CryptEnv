package com.maheshshinde.CryptEnv.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Generates {@code ce_live_*} API keys and stores only SHA-256 hashes.
 */
public final class ApiKeyHasher {

    private static final String PREFIX = "ce_live_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyHasher() {}

    public static String generateRawKey() {
        byte[] extra = new byte[8];
        RANDOM.nextBytes(extra);
        return PREFIX + UUID.randomUUID().toString().replace("-", "") + HexFormat.of().formatHex(extra);
    }

    /** Visible prefix shown in UI (first 16 chars). */
    public static String prefixOf(String rawKey) {
        if (rawKey == null || rawKey.length() < 16) {
            return rawKey;
        }
        return rawKey.substring(0, 16);
    }

    public static String sha256Hex(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

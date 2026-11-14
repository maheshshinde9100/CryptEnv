package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Secret;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Real secret value generators for scheduled / manual rotation.
 */
@Component
public class SecretValueGenerator {

    public enum GeneratorType {
        RANDOM_PASSWORD,
        HEX_TOKEN,
        BASE64_TOKEN,
        UUID,
        RSA_KEYPAIR,
        ED25519_KEYPAIR
    }

    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_";
    private final SecureRandom random = new SecureRandom();

    public String generate(Secret secret) {
        GeneratorType type = resolveType(secret);
        return switch (type) {
            case RANDOM_PASSWORD -> randomPassword(32);
            case HEX_TOKEN -> hexToken(32);
            case BASE64_TOKEN -> base64Token(32);
            case UUID -> UUID.randomUUID().toString();
            case RSA_KEYPAIR -> rsaKeyPairPem();
            case ED25519_KEYPAIR -> ed25519KeyPairPem();
        };
    }

    public GeneratorType resolveType(Secret secret) {
        String key = secret.getKey() == null ? "" : secret.getKey().toUpperCase();
        String desc = secret.getDescription() == null ? "" : secret.getDescription().toUpperCase();
        if (key.contains("RSA") || desc.contains("RSA")) {
            return GeneratorType.RSA_KEYPAIR;
        }
        if (key.contains("ED25519") || key.contains("SSH") || desc.contains("ED25519")) {
            return GeneratorType.ED25519_KEYPAIR;
        }
        if (key.contains("UUID") || desc.contains("UUID")) {
            return GeneratorType.UUID;
        }
        if (key.contains("HEX") || desc.contains("HEX")) {
            return GeneratorType.HEX_TOKEN;
        }
        if (key.contains("TOKEN") || key.contains("API") || key.endsWith("_KEY") || desc.contains("BASE64")) {
            return GeneratorType.BASE64_TOKEN;
        }
        return GeneratorType.RANDOM_PASSWORD;
    }

    public String randomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    public String hexToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String base64Token(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String rsaKeyPairPem() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, random);
            KeyPair pair = gen.generateKeyPair();
            return "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n"
                    + "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException("RSA keypair generation failed", e);
        }
    }

    private String ed25519KeyPairPem() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
            KeyPair pair = gen.generateKeyPair();
            return "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n"
                    + "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----";
        } catch (Exception e) {
            // Fallback when Ed25519 provider unavailable
            return "ed25519:" + base64Token(32) + ":" + base64Token(32);
        }
    }
}

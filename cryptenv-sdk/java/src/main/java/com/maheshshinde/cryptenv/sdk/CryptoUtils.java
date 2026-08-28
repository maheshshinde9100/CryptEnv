package com.maheshshinde.cryptenv.sdk;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class CryptoUtils {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static byte[] deriveKey(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            throw new IllegalArgumentException("Workspace encryption key is required for decryption");
        }
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        byte[] finalKey = new byte[32];
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, 32));
        return finalKey;
    }

    public static String encryptWithKey(String plaintext, String encryptionKey) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] key = deriveKey(encryptionKey);
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[GCM_IV_LENGTH + ciphertextWithTag.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertextWithTag, 0, combined, GCM_IV_LENGTH, ciphertextWithTag.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt secret", e);
        }
    }

    public static String decryptWithKey(String ciphertextB64, String decryptionKey) {
        if (ciphertextB64 == null || ciphertextB64.isEmpty()) {
            return null;
        }

        try {
            byte[] key = deriveKey(decryptionKey);
            byte[] combined = Base64.getDecoder().decode(ciphertextB64);

            if (combined.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data: too short");
            }

            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] ciphertextWithTag = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertextWithTag);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt secret. Verify CRYPTENV_MASTER_KEY matches the workspace encryption key.", e);
        }
    }
}

package com.maheshshinde.CryptEnv.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Local key provider using PBKDF2-HMAC-SHA256 for key wrapping.
 * Each DEK is encrypted with the workspace KEK using AES-256-GCM.
 */
@Component
@Slf4j
public class LocalKeyProvider implements KeyProvider {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_BYTES = 32;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int PBKDF2_SALT_LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateDataKey() {
        byte[] key = new byte[AES_KEY_BYTES];
        random.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Override
    public String wrapKey(String dek, String kek) {
        try {
            byte[] dekBytes = Base64.getDecoder().decode(dek);
            byte[] kekBytes = Base64.getDecoder().decode(kek);

            // Generate salt for this wrap operation
            byte[] salt = new byte[PBKDF2_SALT_LENGTH];
            random.nextBytes(salt);

            // Derive wrapping key from KEK using PBKDF2
            SecretKey wrappingKey = deriveWrappingKey(kekBytes, salt);

            // Generate IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            // Encrypt DEK with wrapping key
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encryptedDek = cipher.doFinal(dekBytes);

            // Format: salt[16] || iv[12] || ciphertext || tag[16]
            ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + encryptedDek.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(encryptedDek);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new EncryptionException("Failed to wrap DEK", e);
        }
    }

    @Override
    public String unwrapKey(String wrappedDek, String kek) {
        try {
            byte[] wrappedBytes = Base64.getDecoder().decode(wrappedDek);
            byte[] kekBytes = Base64.getDecoder().decode(kek);

            if (wrappedBytes.length < PBKDF2_SALT_LENGTH + GCM_IV_LENGTH + 16) {
                throw new EncryptionException("Wrapped DEK too short");
            }

            ByteBuffer buffer = ByteBuffer.wrap(wrappedBytes);
            byte[] salt = new byte[PBKDF2_SALT_LENGTH];
            buffer.get(salt);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encryptedDek = new byte[buffer.remaining()];
            buffer.get(encryptedDek);

            // Derive wrapping key from KEK using PBKDF2
            SecretKey wrappingKey = deriveWrappingKey(kekBytes, salt);

            // Decrypt DEK
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] dekBytes = cipher.doFinal(encryptedDek);

            // Zero out sensitive data
            java.util.Arrays.fill(salt, (byte) 0);

            return Base64.getEncoder().encodeToString(dekBytes);
        } catch (Exception e) {
            throw new EncryptionException("Failed to unwrap DEK - wrong KEK or corrupted data", e);
        }
    }

    @Override
    public Optional<String> rotateWrappedKey(String wrappedDek, String oldKek, String newKek) {
        try {
            String dek = unwrapKey(wrappedDek, oldKek);
            String newWrapped = wrapKey(dek, newKek);
            // Zero out the unwrapped DEK from memory
            dek = null;
            return Optional.of(newWrapped);
        } catch (Exception e) {
            log.warn("Failed to rotate wrapped DEK", e);
            return Optional.empty();
        }
    }

    private SecretKey deriveWrappingKey(byte[] kekBytes, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    new String(kekBytes, StandardCharsets.UTF_8).toCharArray(),
                    salt,
                    PBKDF2_ITERATIONS,
                    AES_KEY_BYTES * 8
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] key = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, ALGORITHM);
        } catch (Exception e) {
            throw new EncryptionException("Failed to derive wrapping key", e);
        }
    }

    @Override
    public String getAlgorithmVersion() {
        return "v1:local-pbkdf2-sha256";
    }
}

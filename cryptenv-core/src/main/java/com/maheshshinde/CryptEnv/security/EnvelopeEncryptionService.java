package com.maheshshinde.CryptEnv.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for envelope encryption operations.
 * Generates per-version DEKs, wraps them with workspace KEK, and encrypts/decrypts secrets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvelopeEncryptionService {

    private final KeyProvider keyProvider;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    @Value("${cryptenv.envelope-encryption.enabled:false}")
    private boolean envelopeEncryptionEnabled;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /**
     * Encrypt a plaintext value using envelope encryption.
     * Returns an EnvelopeResult containing wrapped DEK, IV, tag, and ciphertext.
     */
    public EnvelopeResult encryptWithEnvelope(String plaintext, String workspaceKey) {
        if (!envelopeEncryptionEnabled) {
            // Fall back to direct encryption
            String ciphertext = encryptionService.encryptWithKey(plaintext, workspaceKey);
            return EnvelopeResult.legacy(ciphertext);
        }

        try {
            // Generate a random DEK
            String dek = keyProvider.generateDataKey();
            byte[] dekBytes = Base64.getDecoder().decode(dek);

            // Wrap DEK with workspace KEK
            String wrappedDek = keyProvider.wrapKey(dek, workspaceKey);

            // Generate IV for secret encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            // Encrypt plaintext with DEK
            SecretKey dekKey = new SecretKeySpec(dekBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, dekKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Extract tag (last 16 bytes of GCM output)
            int ciphertextLength = encryptedBytes.length - 16;
            byte[] ciphertext = new byte[ciphertextLength];
            byte[] tag = new byte[16];
            System.arraycopy(encryptedBytes, 0, ciphertext, 0, ciphertextLength);
            System.arraycopy(encryptedBytes, ciphertextLength, tag, 0, 16);

            // Zero out DEK from memory
            java.util.Arrays.fill(dekBytes, (byte) 0);

            return EnvelopeResult.builder()
                    .wrappedDek(wrappedDek)
                    .dekAlgorithm(keyProvider.getAlgorithmVersion())
                    .dekIv(Base64.getEncoder().encodeToString(iv))
                    .dekTag(Base64.getEncoder().encodeToString(tag))
                    .ciphertext(Base64.getEncoder().encodeToString(ciphertext))
                    .build();
        } catch (Exception e) {
            throw new EncryptionException("Envelope encryption failed", e);
        }
    }

    /**
     * Decrypt a ciphertext using envelope encryption.
     * Handles both envelope mode (with wrapped DEK) and legacy mode (direct workspace key).
     */
    public String decryptWithEnvelope(String ciphertext, String workspaceKey,
                                      String wrappedDek, String dekAlgorithm, String dekIv, String dekTag) {
        // Legacy mode: no wrapped DEK means direct encryption
        if (wrappedDek == null || wrappedDek.isBlank()) {
            return encryptionService.decryptWithKey(ciphertext, workspaceKey);
        }

        try {
            // Unwrap DEK using workspace KEK
            String dek = keyProvider.unwrapKey(wrappedDek, workspaceKey);
            byte[] dekBytes = Base64.getDecoder().decode(dek);

            // Reconstruct IV and tag
            byte[] iv = Base64.getDecoder().decode(dekIv);
            byte[] tag = Base64.getDecoder().decode(dekTag);
            byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);

            // Combine ciphertext and tag for GCM decryption
            ByteBuffer buffer = ByteBuffer.allocate(ciphertextBytes.length + tag.length);
            buffer.put(ciphertextBytes);
            buffer.put(tag);
            byte[] encryptedBytes = buffer.array();

            // Decrypt with DEK
            SecretKey dekKey = new SecretKeySpec(dekBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, dekKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            // Zero out DEK from memory
            java.util.Arrays.fill(dekBytes, (byte) 0);

            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            // GCM tag failure - potential tampering or wrong key
            log.error("GCM tag verification failed - potential data tampering or wrong key", e);
            // Log security event (will be called from controller with request context)
            throw new EncryptionException("GCM tag verification failed - data may be tampered or wrong key", e);
        } catch (Exception e) {
            throw new EncryptionException("Envelope decryption failed - wrong key or corrupted data", e);
        }
    }

    /**
     * Rewrap a ciphertext with a new key for master key rotation.
     */
    public String rewrapWithNewKey(String encryptedValue, String oldKeyHash, String newKeyHash) {
        try {
            // Decrypt with old key
            String plaintext = encryptionService.decryptWithKey(encryptedValue, oldKeyHash);
            
            // Encrypt with new key
            String newEncryptedValue = encryptionService.encryptWithKey(plaintext, newKeyHash);
            
            // Zero out plaintext from memory
            char[] plaintextChars = plaintext.toCharArray();
            java.util.Arrays.fill(plaintextChars, '\0');
            
            return newEncryptedValue;
        } catch (Exception e) {
            throw new EncryptionException("Failed to rewrap with new key", e);
        }
    }

    /**
     * Rotate a wrapped DEK to a new workspace key.
     * Returns the new wrapped DEK, or empty if rotation failed.
     */
    public java.util.Optional<String> rotateWrappedDek(String wrappedDek, String oldWorkspaceKey, String newWorkspaceKey) {
        return keyProvider.rotateWrappedKey(wrappedDek, oldWorkspaceKey, newWorkspaceKey);
    }

    public boolean isEnabled() {
        return envelopeEncryptionEnabled;
    }

    /**
     * Result object for envelope encryption operations.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EnvelopeResult {
        private String wrappedDek;
        private String dekAlgorithm;
        private String dekIv;
        private String dekTag;
        private String ciphertext;

        /**
         * Create a legacy result (no envelope encryption).
         */
        public static EnvelopeResult legacy(String ciphertext) {
            return EnvelopeResult.builder()
                    .ciphertext(ciphertext)
                    .build();
        }

        /**
         * Check if this result uses envelope encryption.
         */
        public boolean isEnvelopeMode() {
            return wrappedDek != null && !wrappedDek.isBlank();
        }
    }
}

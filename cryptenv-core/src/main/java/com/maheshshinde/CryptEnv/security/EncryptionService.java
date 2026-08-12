package com.maheshshinde.CryptEnv.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption. Workspace secrets are encrypted with the user workspace key.
 * The platform master key only wraps workspace keys at rest.
 * <p>
 * Wire format (compatible with cryptenv-sdk/node): Base64(IV[12] || ciphertext || tag[16]).
 * Key derivation for arbitrary string keys: UTF-8 bytes padded/truncated to 32 bytes
 * (must stay identical to Node {@code crypto.js} deriveKey).
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_BYTES = 32;

    @Value("${cryptenv.master-key}")
    String masterKey;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(masterKey)) {
            throw new IllegalStateException("cryptenv.master-key / MASTER_ENCRYPTION_KEY is not set");
        }
        // Prefer Base64-decoded length when the value is valid Base64 (production style).
        // Also accept long UTF-8 passphrases used in local/dev and unit tests.
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKey);
            if (decoded.length < 16) {
                throw new IllegalStateException(
                        "MASTER_ENCRYPTION_KEY Base64-decodes to " + decoded.length
                                + " bytes; need at least 16 (prefer 32 for AES-256). "
                                + "Generate with: openssl rand -base64 32");
            }
        } catch (IllegalArgumentException notBase64) {
            if (masterKey.length() < 16) {
                throw new IllegalStateException(
                        "MASTER_ENCRYPTION_KEY must be at least 16 characters (or Base64 of >=16 bytes)");
            }
        }
        log.debug("EncryptionService initialized (master key present)");
    }

    private SecretKeySpec deriveKey(String rawKey) {
        if (rawKey == null) {
            throw new EncryptionException("Encryption key must not be null");
        }
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        byte[] finalKey = new byte[AES_KEY_BYTES];
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, AES_KEY_BYTES));
        return new SecretKeySpec(finalKey, ALGORITHM);
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public String encryptWithKey(String plaintext, String encryptionKey) {
        if (plaintext == null) {
            throw new EncryptionException("Plaintext must not be null");
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new EncryptionException("Encryption key must not be null or blank");
        }
        try {
            SecretKeySpec secretKey = deriveKey(encryptionKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = generateIv();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (EncryptionException e) {
            throw e;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new EncryptionException("Encryption algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new EncryptionException("Invalid encryption key", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new EncryptionException("Invalid algorithm parameter", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    public String decryptWithKey(String ciphertext, String decryptionKey) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new EncryptionException("Ciphertext must not be null or blank");
        }
        if (decryptionKey == null || decryptionKey.isBlank()) {
            throw new EncryptionException("Decryption key must not be null or blank");
        }
        try {
            SecretKeySpec secretKey = deriveKey(decryptionKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            if (decoded.length < GCM_IV_LENGTH + 16) {
                throw new EncryptionException("Ciphertext too short");
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedBytes);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new EncryptionException("Invalid Base64 ciphertext", e);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new EncryptionException("Decryption algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new EncryptionException("Invalid decryption key", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new EncryptionException("Invalid algorithm parameter", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new EncryptionException("Decryption failed - wrong workspace key or corrupted data", e);
        }
    }

    public String encryptWorkspaceKey(String userProvidedWorkspaceKey) {
        return encryptWithKey(userProvidedWorkspaceKey, masterKey);
    }

    public String decryptWorkspaceKey(String encryptedWorkspaceKey) {
        return decryptWithKey(encryptedWorkspaceKey, masterKey);
    }

    /** @deprecated Prefer encryptWorkspaceKey / encryptWithKey */
    @Deprecated
    public String encrypt(String plaintext) {
        return encryptWithKey(plaintext, masterKey);
    }

    /** @deprecated Prefer decryptWorkspaceKey / decryptWithKey */
    @Deprecated
    public String decrypt(String ciphertext) {
        return decryptWithKey(ciphertext, masterKey);
    }

    public String generateDataKey() {
        return generateStrongWorkspaceKey();
    }

    public String generateStrongWorkspaceKey() {
        byte[] key = new byte[AES_KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}

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
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption. Workspace secrets are encrypted with the user workspace key.
 * The platform master key only wraps workspace keys at rest.
 * <p>
 * Wire formats:
 * <ul>
 *   <li>v1 (legacy, Node SDK compatible): Base64(IV[12] || ciphertext || tag[16]),
 *       key = UTF-8 pad/truncate to 32 bytes</li>
 *   <li>v2: {@code v2:} + Base64(salt[16] || IV[12] || ciphertext || tag),
 *       key = PBKDF2-HMAC-SHA256 (120k iters) from passphrase + salt</li>
 * </ul>
 * Optional AAD binds ciphertext to secretId:envId:version (or workspace:env:key:version).
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String V2_PREFIX = "v2:";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_BYTES = 32;
    private static final int PBKDF2_SALT_LENGTH = 16;
    private static final int PBKDF2_ITERATIONS = 120_000;

    @Value("${cryptenv.master-key}")
    String masterKey;

    /** When true, new encryptWithKey calls use PBKDF2 v2 format. Default false for SDK parity. */
    @Value("${cryptenv.encryption.use-pbkdf2:false}")
    boolean usePbkdf2;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(masterKey)) {
            throw new IllegalStateException("cryptenv.master-key / MASTER_ENCRYPTION_KEY is not set");
        }
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
        log.debug("EncryptionService initialized (master key present, pbkdf2={})", usePbkdf2);
    }

    /** Legacy pad/truncate derivation — must stay identical to Node crypto.js deriveKey. */
    private SecretKeySpec deriveKeyV1(String rawKey) {
        if (rawKey == null) {
            throw new EncryptionException("Encryption key must not be null");
        }
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        byte[] finalKey = new byte[AES_KEY_BYTES];
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, AES_KEY_BYTES));
        return new SecretKeySpec(finalKey, ALGORITHM);
    }

    private SecretKeySpec deriveKeyPbkdf2(String passphrase, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BYTES * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] key = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new EncryptionException("PBKDF2 key derivation failed", e);
        }
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private byte[] generateSalt() {
        byte[] salt = new byte[PBKDF2_SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    public String encryptWithKey(String plaintext, String encryptionKey) {
        return encryptWithKey(plaintext, encryptionKey, null);
    }

    public String encryptWithKey(String plaintext, String encryptionKey, String aad) {
        if (plaintext == null) {
            throw new EncryptionException("Plaintext must not be null");
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new EncryptionException("Encryption key must not be null or blank");
        }
        if (usePbkdf2) {
            return encryptV2(plaintext, encryptionKey, aad);
        }
        return encryptV1(plaintext, encryptionKey, aad);
    }

    private String encryptV1(String plaintext, String encryptionKey, String aad) {
        try {
            SecretKeySpec secretKey = deriveKeyV1(encryptionKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = generateIv();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null && !aad.isBlank()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
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

    private String encryptV2(String plaintext, String encryptionKey, String aad) {
        try {
            byte[] salt = generateSalt();
            SecretKeySpec secretKey = deriveKeyPbkdf2(encryptionKey, salt);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = generateIv();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null && !aad.isBlank()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + encryptedBytes.length);
            byteBuffer.put(salt);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);
            return V2_PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    public String decryptWithKey(String ciphertext, String decryptionKey) {
        return decryptWithKey(ciphertext, decryptionKey, null);
    }

    public String decryptWithKey(String ciphertext, String decryptionKey, String aad) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new EncryptionException("Ciphertext must not be null or blank");
        }
        if (decryptionKey == null || decryptionKey.isBlank()) {
            throw new EncryptionException("Decryption key must not be null or blank");
        }
        if (ciphertext.startsWith(V2_PREFIX)) {
            return decryptV2(ciphertext.substring(V2_PREFIX.length()), decryptionKey, aad);
        }
        return decryptV1(ciphertext, decryptionKey, aad);
    }

    private String decryptV1(String ciphertext, String decryptionKey, String aad) {
        try {
            SecretKeySpec secretKey = deriveKeyV1(decryptionKey);
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
            if (aad != null && !aad.isBlank()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
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
            throw new EncryptionException("Decryption failed - wrong workspace key, AAD mismatch, or corrupted data", e);
        }
    }

    private String decryptV2(String payloadB64, String decryptionKey, String aad) {
        try {
            byte[] decoded = Base64.getDecoder().decode(payloadB64);
            if (decoded.length < PBKDF2_SALT_LENGTH + GCM_IV_LENGTH + 16) {
                throw new EncryptionException("v2 ciphertext too short");
            }
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            byte[] salt = new byte[PBKDF2_SALT_LENGTH];
            buf.get(salt);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] encryptedBytes = new byte[buf.remaining()];
            buf.get(encryptedBytes);

            SecretKeySpec secretKey = deriveKeyPbkdf2(decryptionKey, salt);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null && !aad.isBlank()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            Arrays.fill(salt, (byte) 0);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("v2 decryption failed - wrong key, AAD mismatch, or corrupted data", e);
        }
    }

    /** Builds AAD binding string: workspaceId:environmentId:secretKey:version */
    public static String aadFor(Long workspaceId, Long environmentId, String secretKey, int version) {
        return workspaceId + ":" + environmentId + ":" + secretKey + ":" + version;
    }

    public String encryptWorkspaceKey(String userProvidedWorkspaceKey) {
        return encryptWithKey(userProvidedWorkspaceKey, masterKey);
    }

    public String decryptWorkspaceKey(String encryptedWorkspaceKey) {
        return decryptWithKey(encryptedWorkspaceKey, masterKey);
    }

    /**
     * Encrypt an arbitrary payload under the platform master key (not a workspace key).
     * Used for provider credentials and queued sync payloads that must be readable by the
     * background worker without a user security context.
     */
    public String encryptWithMasterKey(String plaintext) {
        return encryptWithKey(plaintext, masterKey);
    }

    /** Decrypt a payload produced by {@link #encryptWithMasterKey(String)}. */
    public String decryptWithMasterKey(String ciphertext) {
        return decryptWithKey(ciphertext, masterKey);
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

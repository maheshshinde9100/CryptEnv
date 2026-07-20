package com.maheshshinde.CryptEnv.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Service
@Slf4j
public class MasterKeyService {

    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;

    @Value("${encryption.master.key}")
    private String masterKeyBase64;

    public String generateMasterKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate master key", e);
            throw new EncryptionException("Failed to generate master key", e);
        }
    }

    public SecretKey getMasterKey() {
        byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String encryptDataKey(String plaintextDataKey) {
        try {
            SecretKey masterKey = getMasterKey();
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey);
            byte[] encrypted = cipher.doFinal(plaintextDataKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            log.error("Encryption algorithm not available", e);
            throw new EncryptionException("Encryption algorithm not available", e);
        } catch (InvalidKeyException e) {
            log.error("Invalid encryption key", e);
            throw new EncryptionException("Invalid encryption key", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Encryption failed", e);
        }
    }

    public String decryptDataKey(String encryptedDataKey) {
        try {
            SecretKey masterKey = getMasterKey();
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedDataKey));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            log.error("Decryption algorithm not available", e);
            throw new EncryptionException("Decryption algorithm not available", e);
        } catch (InvalidKeyException e) {
            log.error("Invalid decryption key", e);
            throw new EncryptionException("Invalid decryption key", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Decryption failed", e);
        }
    }

    public String generateDataKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE);
            SecretKey dataKey = keyGenerator.generateKey();
            String plaintextDataKey = Base64.getEncoder().encodeToString(dataKey.getEncoded());
            String encryptedDataKey = encryptDataKey(plaintextDataKey);
            return encryptedDataKey;
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate data key", e);
            throw new EncryptionException("Failed to generate data key", e);
        }
    }
}

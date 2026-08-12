package com.maheshshinde.CryptEnv.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptionService — AES-256-GCM Unit Tests")
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    private static String randomBase64Aes256Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        ReflectionTestUtils.setField(encryptionService, "masterKey", randomBase64Aes256Key());
        encryptionService.init();
    }

    @Nested
    @DisplayName("encryptWithKey / decryptWithKey round-trip (2-key zero-knowledge model)")
    class WorkspaceKeyRoundTrip {

        @Test
        @DisplayName("encrypt-then-decrypt returns original plaintext")
        void encryptDecryptRoundTrip() {
            String workspaceKey = randomBase64Aes256Key();
            String plaintext = "supersecretpassword:sk-t0p-s3cr3t-p@ss!";

            String ciphertext = encryptionService.encryptWithKey(plaintext, workspaceKey);
            assertNotNull(ciphertext);
            assertNotEquals(plaintext, ciphertext);

            String decrypted = encryptionService.decryptWithKey(ciphertext, workspaceKey);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("same plaintext encrypted twice produces DIFFERENT ciphertext (random IV)")
        void randomIvProducesDifferentCiphertexts() {
            String workspaceKey = randomBase64Aes256Key();
            String plaintext = "duplicate-value";
            String c1 = encryptionService.encryptWithKey(plaintext, workspaceKey);
            String c2 = encryptionService.encryptWithKey(plaintext, workspaceKey);
            assertNotEquals(c1, c2, "AES-GCM IV must be random so same PT != same CT");
        }

        @Test
        @DisplayName("decrypt with WRONG key throws EncryptionException, never returns plaintext")
        void wrongKeyThrows() {
            String keyA = randomBase64Aes256Key();
            String keyB = randomBase64Aes256Key();
            String ct = encryptionService.encryptWithKey("data", keyA);
            assertThrows(EncryptionException.class,
                    () -> encryptionService.decryptWithKey(ct, keyB));
        }

        @Test
        @DisplayName("tampered ciphertext fails GCM auth tag check")
        void tamperedCiphertextFails() {
            String wk = randomBase64Aes256Key();
            String ct = encryptionService.encryptWithKey("hello", wk);
            byte[] ctBytes = Base64.getDecoder().decode(ct);
            ctBytes[ctBytes.length / 2] ^= 0x01;
            String tampered = Base64.getEncoder().encodeToString(ctBytes);
            assertThrows(EncryptionException.class,
                    () -> encryptionService.decryptWithKey(tampered, wk));
        }

        @Test
        @DisplayName("empty or null values handled safely without NPE")
        void emptyAndNullValues() {
            String wk = randomBase64Aes256Key();
            String ct = encryptionService.encryptWithKey("", wk);
            assertNotNull(ct);
            assertEquals("", encryptionService.decryptWithKey(ct, wk));

            assertThrows(EncryptionException.class,
                    () -> encryptionService.encryptWithKey(null, wk));
        }

        @Test
        @DisplayName("long values (10KB) round-trip correctly")
        void longValueRoundTrip() {
            String wk = randomBase64Aes256Key();
            StringBuilder sb = new StringBuilder(10_000);
            for (int i = 0; i < 10_000; i++) sb.append((char) ('a' + (i % 26)));
            String pt = sb.toString();
            String ct = encryptionService.encryptWithKey(pt, wk);
            assertEquals(pt, encryptionService.decryptWithKey(ct, wk));
        }

        @Test
        @DisplayName("Unicode / emoji payloads survive AES-GCM")
        void unicodeSurvives() {
            String wk = randomBase64Aes256Key();
            String pt = "你好 程序员 confidential";
            String ct = encryptionService.encryptWithKey(pt, wk);
            assertEquals(pt, encryptionService.decryptWithKey(ct, wk));
        }
    }

    @Nested
    @DisplayName("Master-key wrapping (workspace keys encrypted at rest in DB)")
    class MasterKeyWrapping {

        @Test
        @DisplayName("wrap then unwrap workspace key returns original bytes")
        void wrapUnwrapWorkspaceKey() {
            String rawWorkspaceKey = randomBase64Aes256Key();
            String wrapped = encryptionService.encrypt(rawWorkspaceKey);
            assertNotNull(wrapped);
            assertNotEquals(rawWorkspaceKey, wrapped);
            String unwrapped = encryptionService.decrypt(wrapped);
            assertEquals(rawWorkspaceKey, unwrapped);
        }

        @Test
        @DisplayName("wrapped workspace keys are never equal to each other (non-deterministic)")
        void wrappingIsNonDeterministic() {
            String wk = randomBase64Aes256Key();
            String w1 = encryptionService.encrypt(wk);
            String w2 = encryptionService.encrypt(wk);
            assertNotEquals(w1, w2, "Master-key wrapped values must be non-deterministic");
        }

        @Test
        @DisplayName("init() fails if master key is weak (<16 chars / <16 decoded bytes)")
        void weakMasterKeyRejected() {
            EncryptionService weak = new EncryptionService();
            ReflectionTestUtils.setField(weak, "masterKey", "d2Vhaw=="); // "weak" base64 — 4 bytes
            assertThrows(RuntimeException.class, weak::init);
        }
    }

    @Nested
    @DisplayName("Output format validation")
    class FormatValidation {

        @Test
        @DisplayName("encryptWithKey outputs valid Base64")
        void outputIsValidBase64() {
            String wk = randomBase64Aes256Key();
            String ct = encryptionService.encryptWithKey("payload", wk);
            assertNotNull(Base64.getDecoder().decode(ct));
        }

        @Test
        @DisplayName("ciphertext is long enough for 12-byte IV + 16-byte GCM tag + payload")
        void ciphertextSize() {
            String wk = randomBase64Aes256Key();
            String ct = encryptionService.encryptWithKey("x", wk);
            int bytes = Base64.getDecoder().decode(ct).length;
            assertTrue(bytes >= 29,
                    "ciphertext " + bytes + " bytes < minimum 29 (12 IV + 16 tag + payload)");
        }
    }
}

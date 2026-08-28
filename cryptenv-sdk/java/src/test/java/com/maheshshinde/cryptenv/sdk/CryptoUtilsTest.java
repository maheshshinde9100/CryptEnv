package com.maheshshinde.cryptenv.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoUtilsTest {

    private static final String KEY = "test-workspace-encryption-key-12345678";

    @Test
    public void testEncryptDecryptRoundtrip() {
        String plaintext = "postgres://user:pass@host:5432/db";
        String cipher = CryptoUtils.encryptWithKey(plaintext, KEY);
        assertNotNull(cipher);
        assertNotEquals(plaintext, cipher);

        String decrypted = CryptoUtils.decryptWithKey(cipher, KEY);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void testEncryptDecryptEmptyString() {
        String cipher = CryptoUtils.encryptWithKey("", KEY);
        assertNotNull(cipher);
        String decrypted = CryptoUtils.decryptWithKey(cipher, KEY);
        assertEquals("", decrypted);
    }

    @Test
    public void testDecryptNullReturnsNull() {
        assertNull(CryptoUtils.decryptWithKey(null, KEY));
        assertNull(CryptoUtils.decryptWithKey("", KEY));
    }

    @Test
    public void testEncryptNullReturnsNull() {
        assertNull(CryptoUtils.encryptWithKey(null, KEY));
    }

    @Test
    public void testDifferentIvProducesDifferentCiphertext() {
        String plaintext = "same-value";
        String c1 = CryptoUtils.encryptWithKey(plaintext, KEY);
        String c2 = CryptoUtils.encryptWithKey(plaintext, KEY);
        assertNotEquals(c1, c2, "Two encryptions of same value must differ (random IV)");

        assertEquals(CryptoUtils.decryptWithKey(c1, KEY), plaintext);
        assertEquals(CryptoUtils.decryptWithKey(c2, KEY), plaintext);
    }

    @Test
    public void testTamperedCiphertextFails() {
        String cipher = CryptoUtils.encryptWithKey("secret-value", KEY);
        byte[] bytes = java.util.Base64.getDecoder().decode(cipher);
        bytes[bytes.length - 1] ^= 0xFF;
        String tampered = java.util.Base64.getEncoder().encodeToString(bytes);

        assertThrows(RuntimeException.class, () -> CryptoUtils.decryptWithKey(tampered, KEY));
    }

    @Test
    public void testWrongKeyFails() {
        String cipher = CryptoUtils.encryptWithKey("hello", KEY);
        assertThrows(RuntimeException.class,
                () -> CryptoUtils.decryptWithKey(cipher, "different-key-entirely-1234"));
    }

    @Test
    public void testShortCiphertextFails() {
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.decryptWithKey("AAAA", KEY));
    }

    @Test
    public void testEmptyKeyFails() {
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.encryptWithKey("val", ""));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.encryptWithKey("val", null));
    }

    @Test
    public void testShortKeyPaddedCorrectly() {
        String shortKey = "short";
        String plain = "my-secret";
        String c = CryptoUtils.encryptWithKey(plain, shortKey);
        String d = CryptoUtils.decryptWithKey(c, shortKey);
        assertEquals(plain, d);
    }

    @Test
    public void testLongKeyTruncatedCorrectly() {
        String longKey = "a".repeat(100);
        String plain = "another-secret";
        String c = CryptoUtils.encryptWithKey(plain, longKey);
        String d = CryptoUtils.decryptWithKey(c, longKey);
        assertEquals(plain, d);
    }

    @Test
    public void testUnicodePlaintextSurvives() {
        String unicode = "Hello 世界 🚀 password=§$%&";
        String c = CryptoUtils.encryptWithKey(unicode, KEY);
        String d = CryptoUtils.decryptWithKey(c, KEY);
        assertEquals(unicode, d);
    }

    @Test
    public void testBase64FormatIsValid() {
        String c = CryptoUtils.encryptWithKey("plain", KEY);
        assertNotNull(c);
        assertTrue(c.matches("[A-Za-z0-9+/=]+"), "Must be valid Base64 alphabet");
        java.util.Base64.getDecoder().decode(c);
    }
}

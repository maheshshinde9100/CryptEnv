package com.maheshshinde.CryptEnv.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalKeyProviderTest {

    private LocalKeyProvider keyProvider;

    @BeforeEach
    void setUp() {
        keyProvider = new LocalKeyProvider();
    }

    @Test
    void testGenerateDataKey() {
        String dek = keyProvider.generateDataKey();
        assertNotNull(dek);
        assertFalse(dek.isBlank());
        // Base64 encoded 32 bytes = 44 chars
        assertTrue(dek.length() >= 40);
    }

    @Test
    void testWrapAndUnwrapKey() {
        String dek = keyProvider.generateDataKey();
        String kek = keyProvider.generateDataKey(); // Use another key as KEK for testing

        String wrapped = keyProvider.wrapKey(dek, kek);
        assertNotNull(wrapped);
        assertFalse(wrapped.isBlank());
        assertNotEquals(dek, wrapped);

        String unwrapped = keyProvider.unwrapKey(wrapped, kek);
        assertEquals(dek, unwrapped);
    }

    @Test
    void testUnwrapWithWrongKeyFails() {
        String dek = keyProvider.generateDataKey();
        String kek = keyProvider.generateDataKey();
        String wrongKek = keyProvider.generateDataKey();

        String wrapped = keyProvider.wrapKey(dek, kek);

        assertThrows(EncryptionException.class, () -> {
            keyProvider.unwrapKey(wrapped, wrongKek);
        });
    }

    @Test
    void testRotateWrappedKey() {
        String dek = keyProvider.generateDataKey();
        String oldKek = keyProvider.generateDataKey();
        String newKek = keyProvider.generateDataKey();

        String wrapped = keyProvider.wrapKey(dek, oldKek);

        var rotated = keyProvider.rotateWrappedKey(wrapped, oldKek, newKek);
        assertTrue(rotated.isPresent());

        // Verify new wrapped key works with new KEK
        String unwrapped = keyProvider.unwrapKey(rotated.get(), newKek);
        assertEquals(dek, unwrapped);

        // Verify old wrapped key doesn't work with new KEK
        assertThrows(EncryptionException.class, () -> {
            keyProvider.unwrapKey(wrapped, newKek);
        });
    }

    @Test
    void testGetAlgorithmVersion() {
        String version = keyProvider.getAlgorithmVersion();
        assertNotNull(version);
        assertTrue(version.contains("local-pbkdf2"));
    }
}

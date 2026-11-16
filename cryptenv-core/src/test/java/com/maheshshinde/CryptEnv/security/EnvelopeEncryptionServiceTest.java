package com.maheshshinde.CryptEnv.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvelopeEncryptionServiceTest {

    @Mock
    private KeyProvider keyProvider;

    @Mock
    private EncryptionService encryptionService;

    private EnvelopeEncryptionService envelopeEncryptionService;

    @BeforeEach
    void setUp() {
        envelopeEncryptionService = new EnvelopeEncryptionService(keyProvider, encryptionService);
    }

    @Test
    void testEncryptWithEnvelope_Disabled() {
        when(encryptionService.encryptWithKey(anyString(), anyString())).thenReturn("legacy-ciphertext");

        EnvelopeEncryptionService.EnvelopeResult result = envelopeEncryptionService.encryptWithEnvelope("plaintext", "workspace-key");

        assertNotNull(result);
        assertEquals("legacy-ciphertext", result.getCiphertext());
        assertFalse(result.isEnvelopeMode());
        assertNull(result.getWrappedDek());
    }

    @Test
    void testDecryptWithEnvelope_LegacyMode() {
        when(encryptionService.decryptWithKey(anyString(), anyString())).thenReturn("plaintext");

        String decrypted = envelopeEncryptionService.decryptWithEnvelope(
                "ciphertext", "workspace-key", null, null, null, null);

        assertEquals("plaintext", decrypted);
    }

    @Test
    void testIsEnabled() {
        assertFalse(envelopeEncryptionService.isEnabled());
    }
}

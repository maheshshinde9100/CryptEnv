package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecretValueGenerator")
class SecretValueGeneratorTest {

    private final SecretValueGenerator generator = new SecretValueGenerator();

    @Test
    void generatesRandomPasswordByDefault() {
        String value = generator.generate(Secret.builder().key("DB_PASSWORD").build());
        assertNotNull(value);
        assertEquals(32, value.length());
        assertFalse(value.startsWith("rotated_"));
    }

    @Test
    void generatesUuidWhenKeyHintsUuid() {
        String value = generator.generate(Secret.builder().key("SESSION_UUID").build());
        assertDoesNotThrow(() -> java.util.UUID.fromString(value));
    }

    @Test
    void generatesHexToken() {
        String value = generator.generate(Secret.builder().key("HEX_SECRET").build());
        assertEquals(64, value.length());
        assertTrue(value.matches("[0-9a-f]+"));
    }

    @Test
    void generatesBase64TokenForApiKeys() {
        String value = generator.generate(Secret.builder().key("API_TOKEN").build());
        assertFalse(value.isBlank());
        assertTrue(value.length() >= 40);
    }
}

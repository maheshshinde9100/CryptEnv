package com.maheshshinde.CryptEnv.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EnvValidationConfig — Startup Safety Checks")
class EnvValidationConfigTest {

    private EnvValidationConfig validator;

    private static final String VALID_JWT = "a".repeat(64);
    private static final String VALID_MASTER = "dGVzdC10ZXN0LXRlc3QtdGVzdC10ZXN0LXRlc3QtdGU=";
    private static final String VALID_DB_URL = "jdbc:postgresql://localhost:5432/cryptenv";

    @BeforeEach
    void setUp() {
        validator = new EnvValidationConfig();
    }

    private void setFields(String dbUrl, String dbUser, String dbPass, String jwt, String mk) {
        ReflectionTestUtils.setField(validator, "dbUrl", dbUrl);
        ReflectionTestUtils.setField(validator, "dbUser", dbUser);
        ReflectionTestUtils.setField(validator, "dbPass", dbPass);
        ReflectionTestUtils.setField(validator, "jwtSecret", jwt);
        ReflectionTestUtils.setField(validator, "masterKey", mk);
    }

    @Test
    @DisplayName("VALID env vars — validator passes without exception")
    void validEnvPasses() {
        setFields(VALID_DB_URL, "postgres", "pw", VALID_JWT, VALID_MASTER);
        assertDoesNotThrow(() -> validator.validate());
    }

    @Test
    @DisplayName("Missing DB_URL → IllegalStateException")
    void missingDbUrl() {
        setFields("__MISSING__", "u", "p", VALID_JWT, VALID_MASTER);
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("Missing DB_USER → IllegalStateException")
    void missingDbUser() {
        setFields(VALID_DB_URL, "__MISSING__", "p", VALID_JWT, VALID_MASTER);
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("Missing JWT_SECRET → IllegalStateException")
    void missingJwt() {
        setFields(VALID_DB_URL, "u", "p", "__MISSING__", VALID_MASTER);
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("JWT too short (25 chars) → rejected")
    void jwtTooShort() {
        setFields(VALID_DB_URL, "u", "p", "x".repeat(25), VALID_MASTER);
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("MASTER key not valid Base64 → rejected")
    void masterKeyNotBase64() {
        setFields(VALID_DB_URL, "u", "p", VALID_JWT, "!!!this-is-not-valid-base64!!!");
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("MASTER key only 16 bytes decoded (weak) → rejected")
    void masterKey16Bytes() {
        String weak16 = "dGVzdC10ZXN0LXRlc3QtdGVzdA=="; // 16 bytes decoded
        setFields(VALID_DB_URL, "u", "p", VALID_JWT, weak16);
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("DB_URL must start with jdbc:postgresql:// — MySQL URL rejected")
    void dbUrlWrongPrefix() {
        setFields("jdbc:mysql://localhost:3306/x", "u", "p", VALID_JWT, VALID_MASTER);
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("Exception message banner contains actionable recovery text")
    void errorMessageBannerFormat() {
        setFields("__MISSING__", "u", "p", "x".repeat(10), VALID_MASTER);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        String msg = ex.getMessage();
        assertTrue(msg.contains("MISSING OR INVALID"));
        assertTrue(msg.contains("DB_URL"));
        assertTrue(msg.contains("JWT_SECRET"));
        assertTrue(msg.contains("openssl rand"));
    }

    @Test
    @DisplayName("Empty string env vars treated same as missing")
    void emptyStringTreatedAsMissing() {
        setFields("", "u", "p", VALID_JWT, VALID_MASTER);
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }
}

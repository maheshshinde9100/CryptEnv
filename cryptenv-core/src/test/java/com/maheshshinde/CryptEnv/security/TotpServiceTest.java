package com.maheshshinde.CryptEnv.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TotpService")
class TotpServiceTest {

    @Test
    void base32RoundTrip() {
        byte[] original = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        String encoded = TotpService.Base32.encode(original);
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", encoded);
        assertArrayEquals(original, TotpService.Base32.decode(encoded));
    }

    @Test
    void rfc6238Sha1VectorsSixDigits() {
        // RFC 6238 Appendix B secret (ASCII) as Base32
        String secret = TotpService.Base32.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

        // 8-digit RFC values truncated to 6 digits (mod 1_000_000)
        assertEquals("287082", TotpService.generateCode(secret, 59L));
        assertEquals("081804", TotpService.generateCode(secret, 1111111109L));
        assertEquals("050471", TotpService.generateCode(secret, 1111111111L));
        assertEquals("005924", TotpService.generateCode(secret, 1234567890L));
        assertEquals("279037", TotpService.generateCode(secret, 2000000000L));
    }

    @Test
    void verifyAcceptsCurrentAndAdjacentWindow() {
        String secret = TotpService.generateSecret();
        long now = System.currentTimeMillis() / 1000L;
        String code = TotpService.generateCode(secret, now);
        assertTrue(TotpService.verify(secret, code, now, 1));
        assertTrue(TotpService.verify(secret, code, now + 30, 1));
        assertTrue(TotpService.verify(secret, code, now - 30, 1));
        assertFalse(TotpService.verify(secret, code, now + 120, 1));
        assertFalse(TotpService.verify(secret, "000000", now, 1));
        assertFalse(TotpService.verify(secret, "abcdef", now, 1));
    }

    @Test
    void otpAuthUriContainsIssuerAndSecret() {
        String secret = "JBSWY3DPEHPK3PXP";
        String uri = TotpService.otpAuthUri("CryptEnv", "user@example.com", secret);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=" + secret));
        assertTrue(uri.contains("issuer=CryptEnv"));
        assertTrue(uri.contains("algorithm=SHA1"));
    }

    @Test
    void generateSecretIsValidBase32() {
        String secret = TotpService.generateSecret();
        assertFalse(secret.isBlank());
        assertDoesNotThrow(() -> TotpService.Base32.decode(secret));
        String code = TotpService.generateCode(secret);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }
}

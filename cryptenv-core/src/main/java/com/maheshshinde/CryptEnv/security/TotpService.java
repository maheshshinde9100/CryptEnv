package com.maheshshinde.CryptEnv.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * RFC 6238 TOTP using HMAC-SHA1, 30-second steps, 6-digit codes.
 * Pure Java — no third-party OTP library.
 */
public final class TotpService {

    public static final int DEFAULT_DIGITS = 6;
    public static final int DEFAULT_PERIOD_SECONDS = 30;
    public static final int DEFAULT_WINDOW = 1; // allow ±1 time step
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpService() {}

    /** Generates a 20-byte secret and returns it as Base32 (no padding). */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    public static String generateCode(String base32Secret) {
        return generateCode(base32Secret, System.currentTimeMillis() / 1000L);
    }

    public static String generateCode(String base32Secret, long unixTimeSeconds) {
        long counter = unixTimeSeconds / DEFAULT_PERIOD_SECONDS;
        return truncate(hmacSha1(Base32.decode(base32Secret), counter), DEFAULT_DIGITS);
    }

    public static boolean verify(String base32Secret, String code) {
        return verify(base32Secret, code, System.currentTimeMillis() / 1000L, DEFAULT_WINDOW);
    }

    public static boolean verify(String base32Secret, String code, long unixTimeSeconds, int window) {
        if (code == null || !code.matches("\\d{" + DEFAULT_DIGITS + "}")) {
            return false;
        }
        long counter = unixTimeSeconds / DEFAULT_PERIOD_SECONDS;
        for (int i = -window; i <= window; i++) {
            String expected = truncate(hmacSha1(Base32.decode(base32Secret), counter + i), DEFAULT_DIGITS);
            if (constantTimeEquals(expected, code)) {
                return true;
            }
        }
        return false;
    }

    public static String otpAuthUri(String issuer, String accountName, String base32Secret) {
        String label = urlEncode(issuer) + ":" + urlEncode(accountName);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + DEFAULT_DIGITS
                + "&period=" + DEFAULT_PERIOD_SECONDS;
    }

    private static byte[] hmacSha1(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
    }

    private static String truncate(byte[] hash, int digits) {
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, digits);
        return String.format(Locale.ROOT, "%0" + digits + "d", otp);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Minimal Base32 (RFC 4648) encoder/decoder without padding. */
    public static final class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        private Base32() {}

        public static String encode(byte[] data) {
            StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
            int buffer = 0;
            int bitsLeft = 0;
            for (byte b : data) {
                buffer = (buffer << 8) | (b & 0xFF);
                bitsLeft += 8;
                while (bitsLeft >= 5) {
                    sb.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                    bitsLeft -= 5;
                }
            }
            if (bitsLeft > 0) {
                sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
            }
            return sb.toString();
        }

        public static byte[] decode(String encoded) {
            String s = encoded.trim().replace("=", "").toUpperCase(Locale.ROOT);
            int length = s.length() * 5 / 8;
            byte[] out = new byte[length];
            int buffer = 0;
            int bitsLeft = 0;
            int index = 0;
            for (int i = 0; i < s.length(); i++) {
                int val = ALPHABET.indexOf(s.charAt(i));
                if (val < 0) {
                    throw new IllegalArgumentException("Invalid Base32 character");
                }
                buffer = (buffer << 5) | val;
                bitsLeft += 5;
                if (bitsLeft >= 8) {
                    out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                    bitsLeft -= 8;
                }
            }
            return out;
        }
    }
}

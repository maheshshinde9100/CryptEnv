package com.maheshshinde.CryptEnv.provider;

import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.engines.Salsa20Engine;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.math.ec.rfc7748.X25519;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates the GitHub sealed-box implementation:
 * <ol>
 *   <li>HSalsa20 + Salsa20 must reproduce BouncyCastle's XSalsa20 keystream.</li>
 *   <li>A sealed box must decrypt back to the original message with the recipient secret key,
 *       and the Poly1305 tag must verify.</li>
 * </ol>
 */
class SealedBoxTest {

    private static final byte[] ZERO_16 = new byte[16];

    @Test
    void hsalsa20DerivedStreamMatchesBouncyCastleXSalsa20() {
        SecureRandom rnd = new SecureRandom();
        byte[] key = new byte[32];
        rnd.nextBytes(key);
        byte[] nonce = new byte[24];
        rnd.nextBytes(nonce);
        int length = 200;

        XSalsa20Engine reference = new XSalsa20Engine();
        reference.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
        byte[] expected = new byte[length];
        reference.processBytes(expected, 0, length, expected, 0);

        byte[] subKey = SealedBox.hsalsa20(key, Arrays.copyOfRange(nonce, 0, 16));
        Salsa20Engine salsa = new Salsa20Engine();
        salsa.init(true, new ParametersWithIV(new KeyParameter(subKey), Arrays.copyOfRange(nonce, 16, 24)));
        byte[] actual = new byte[length];
        salsa.processBytes(actual, 0, length, actual, 0);

        assertArrayEquals(expected, actual, "HSalsa20-derived keystream must match XSalsa20");
    }

    @Test
    void sealedBoxRoundTripsWithRecipientSecretKey() {
        SecureRandom rnd = new SecureRandom();
        byte[] recipientSecret = new byte[32];
        rnd.nextBytes(recipientSecret);
        byte[] recipientPublic = new byte[32];
        X25519.generatePublicKey(recipientSecret, 0, recipientPublic, 0);

        byte[] message = "super-secret-value-123".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = SealedBox.seal(message, recipientPublic);

        assertEquals(32 + message.length + 16, sealed.length, "sealed length = epk + ciphertext + tag");

        byte[] ephemeralPublic = Arrays.copyOfRange(sealed, 0, 32);
        byte[] ciphertext = Arrays.copyOfRange(sealed, 32, sealed.length - 16);
        byte[] tag = Arrays.copyOfRange(sealed, sealed.length - 16, sealed.length);

        byte[] nonce = new byte[24];
        Blake2bDigest blake = new Blake2bDigest(24 * 8);
        blake.update(ephemeralPublic, 0, ephemeralPublic.length);
        blake.update(recipientPublic, 0, recipientPublic.length);
        blake.doFinal(nonce, 0);

        byte[] shared = new byte[32];
        X25519.scalarMult(recipientSecret, 0, ephemeralPublic, 0, shared, 0);
        byte[] key = SealedBox.hsalsa20(shared, ZERO_16);

        byte[] subKey = SealedBox.hsalsa20(key, Arrays.copyOfRange(nonce, 0, 16));
        Salsa20Engine salsa = new Salsa20Engine();
        salsa.init(true, new ParametersWithIV(new KeyParameter(subKey), Arrays.copyOfRange(nonce, 16, 24)));
        byte[] stream = new byte[32 + ciphertext.length];
        salsa.processBytes(stream, 0, stream.length, stream, 0);

        byte[] plaintext = new byte[ciphertext.length];
        for (int i = 0; i < ciphertext.length; i++) {
            plaintext[i] = (byte) (ciphertext[i] ^ stream[32 + i]);
        }
        assertArrayEquals(message, plaintext, "sealed box must decrypt back to the message");

        Poly1305 poly = new Poly1305();
        poly.init(new KeyParameter(stream, 0, 32));
        poly.update(ciphertext, 0, ciphertext.length);
        byte[] expectedTag = new byte[16];
        poly.doFinal(expectedTag, 0);
        assertArrayEquals(expectedTag, tag, "Poly1305 tag must verify");
    }
}

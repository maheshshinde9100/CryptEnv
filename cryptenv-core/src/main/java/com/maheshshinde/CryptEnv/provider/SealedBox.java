package com.maheshshinde.CryptEnv.provider;

import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.engines.Salsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.math.ec.rfc7748.X25519;

import java.security.SecureRandom;

/**
 * Implementation of libsodium's {@code crypto_box_seal} (anonymous sealed box), required by the
 * GitHub Actions secrets API to encrypt secret values with a repository public key.
 *
 * <p>Construction: {@code sealed = ephemeral_pk || secretbox(beforenm(sk_eph, pk), nonce, message)}
 * where {@code nonce = blake2b-24(ephemeral_pk || recipient_pk)} and
 * {@code beforenm = HSalsa20(X25519(sk_eph, pk), 0)}.
 *
 * <p>See https://libsodium.gitbook.io/doc/public-key_cryptography/sealed_boxes
 */
public final class SealedBox {

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 24;
    private static final int MAC_BYTES = 16;
    private static final byte[] ZERO_16 = new byte[16];

    static {
        // Required once before any X25519.scalarMult call
        X25519.precompute();
    }

    private SealedBox() {
    }

    /** Encrypt {@code message} to a 32-byte X25519 public key; returns {@code epk || ciphertext || tag}. */
    public static byte[] seal(byte[] message, byte[] recipientPublicKey) {
        if (recipientPublicKey == null || recipientPublicKey.length != KEY_BYTES) {
            throw new ProviderException("Invalid X25519 public key length");
        }

        byte[] ephemeralSecret = new byte[KEY_BYTES];
        byte[] ephemeralPublic = new byte[KEY_BYTES];
        X25519.generatePrivateKey(new SecureRandom(), ephemeralSecret);
        X25519.generatePublicKey(ephemeralSecret, 0, ephemeralPublic, 0);

        byte[] nonce = new byte[NONCE_BYTES];
        Blake2bDigest blake = new Blake2bDigest(NONCE_BYTES * 8);
        blake.update(ephemeralPublic, 0, ephemeralPublic.length);
        blake.update(recipientPublicKey, 0, recipientPublicKey.length);
        blake.doFinal(nonce, 0);

        byte[] sharedSecret = new byte[KEY_BYTES];
        X25519.scalarMult(ephemeralSecret, 0, recipientPublicKey, 0, sharedSecret, 0);

        byte[] key = hsalsa20(sharedSecret, ZERO_16);
        byte[] ciphertext = secretBox(message, nonce, key);

        byte[] sealed = new byte[KEY_BYTES + ciphertext.length];
        System.arraycopy(ephemeralPublic, 0, sealed, 0, KEY_BYTES);
        System.arraycopy(ciphertext, 0, sealed, KEY_BYTES, ciphertext.length);
        return sealed;
    }

    /** NaCl {@code crypto_secretbox}: ciphertext followed by a 16-byte Poly1305 tag. */
    private static byte[] secretBox(byte[] message, byte[] nonce, byte[] key) {
        byte[] stream = xsalsa20Stream(key, nonce, KEY_BYTES + message.length);
        byte[] ciphertext = new byte[message.length];
        for (int i = 0; i < message.length; i++) {
            ciphertext[i] = (byte) (message[i] ^ stream[KEY_BYTES + i]);
        }

        Poly1305 poly = new Poly1305();
        poly.init(new KeyParameter(stream, 0, KEY_BYTES));
        poly.update(ciphertext, 0, ciphertext.length);
        byte[] tag = new byte[MAC_BYTES];
        poly.doFinal(tag, 0);

        byte[] output = new byte[ciphertext.length + MAC_BYTES];
        System.arraycopy(ciphertext, 0, output, 0, ciphertext.length);
        System.arraycopy(tag, 0, output, ciphertext.length, MAC_BYTES);
        return output;
    }

    /** XSalsa20 keystream of {@code length} bytes (counter starts at 0). */
    private static byte[] xsalsa20Stream(byte[] key, byte[] nonce, int length) {
        byte[] subKey = hsalsa20(key, java.util.Arrays.copyOfRange(nonce, 0, 16));
        byte[] tail = java.util.Arrays.copyOfRange(nonce, 16, 24);

        Salsa20Engine salsa = new Salsa20Engine();
        salsa.init(true, new ParametersWithIV(new KeyParameter(subKey), tail));
        byte[] out = new byte[length];
        salsa.processBytes(out, 0, length, out, 0);
        return out;
    }

    /**
     * HSalsa20 key-derivation core: 20 rounds of the Salsa20 doubleround without the final
     * feed-forward, outputting words 0,5,10,15,6,7,8,9.
     */
    static byte[] hsalsa20(byte[] key, byte[] input16) {
        int[] x = new int[16];
        x[0] = 0x61707865;
        x[5] = 0x3320646e;
        x[10] = 0x79622d32;
        x[15] = 0x6b206574;

        int[] k = littleEndianWords(key, 8);
        x[1] = k[0];
        x[2] = k[1];
        x[3] = k[2];
        x[4] = k[3];
        x[11] = k[4];
        x[12] = k[5];
        x[13] = k[6];
        x[14] = k[7];

        int[] n = littleEndianWords(input16, 4);
        x[6] = n[0];
        x[7] = n[1];
        x[8] = n[2];
        x[9] = n[3];

        for (int i = 0; i < 10; i++) {
            doubleRound(x);
        }

        int[] out = new int[8];
        out[0] = x[0];
        out[1] = x[5];
        out[2] = x[10];
        out[3] = x[15];
        out[4] = x[6];
        out[5] = x[7];
        out[6] = x[8];
        out[7] = x[9];
        return wordsToLittleEndian(out);
    }

    private static void doubleRound(int[] x) {
        // column round
        x[4] ^= rotl(x[0] + x[12], 7);
        x[8] ^= rotl(x[4] + x[0], 9);
        x[12] ^= rotl(x[8] + x[4], 13);
        x[0] ^= rotl(x[12] + x[8], 18);
        x[9] ^= rotl(x[5] + x[1], 7);
        x[13] ^= rotl(x[9] + x[5], 9);
        x[1] ^= rotl(x[13] + x[9], 13);
        x[5] ^= rotl(x[1] + x[13], 18);
        x[14] ^= rotl(x[10] + x[6], 7);
        x[2] ^= rotl(x[14] + x[10], 9);
        x[6] ^= rotl(x[2] + x[14], 13);
        x[10] ^= rotl(x[6] + x[2], 18);
        x[3] ^= rotl(x[15] + x[11], 7);
        x[7] ^= rotl(x[3] + x[15], 9);
        x[11] ^= rotl(x[7] + x[3], 13);
        x[15] ^= rotl(x[11] + x[7], 18);
        // row round
        x[1] ^= rotl(x[0] + x[3], 7);
        x[2] ^= rotl(x[1] + x[0], 9);
        x[3] ^= rotl(x[2] + x[1], 13);
        x[0] ^= rotl(x[3] + x[2], 18);
        x[6] ^= rotl(x[5] + x[4], 7);
        x[7] ^= rotl(x[6] + x[5], 9);
        x[4] ^= rotl(x[7] + x[6], 13);
        x[5] ^= rotl(x[4] + x[7], 18);
        x[11] ^= rotl(x[10] + x[9], 7);
        x[8] ^= rotl(x[11] + x[10], 9);
        x[9] ^= rotl(x[8] + x[11], 13);
        x[10] ^= rotl(x[9] + x[8], 18);
        x[12] ^= rotl(x[15] + x[14], 7);
        x[13] ^= rotl(x[12] + x[15], 9);
        x[14] ^= rotl(x[13] + x[12], 13);
        x[15] ^= rotl(x[14] + x[13], 18);
    }

    private static int rotl(int value, int shift) {
        return (value << shift) | (value >>> (32 - shift));
    }

    private static int[] littleEndianWords(byte[] bytes, int count) {
        int[] words = new int[count];
        for (int i = 0; i < count; i++) {
            words[i] = (bytes[i * 4] & 0xff)
                    | ((bytes[i * 4 + 1] & 0xff) << 8)
                    | ((bytes[i * 4 + 2] & 0xff) << 16)
                    | ((bytes[i * 4 + 3] & 0xff) << 24);
        }
        return words;
    }

    private static byte[] wordsToLittleEndian(int[] words) {
        byte[] bytes = new byte[words.length * 4];
        for (int i = 0; i < words.length; i++) {
            bytes[i * 4] = (byte) words[i];
            bytes[i * 4 + 1] = (byte) (words[i] >>> 8);
            bytes[i * 4 + 2] = (byte) (words[i] >>> 16);
            bytes[i * 4 + 3] = (byte) (words[i] >>> 24);
        }
        return bytes;
    }
}

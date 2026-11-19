package com.maheshshinde.CryptEnv.security;

import java.util.Optional;

/**
 * Key provider interface for envelope encryption.
 * Implementations can use local KDF (Argon2id/PBKDF2) or external KMS (OpenBao/Vault Transit).
 */
public interface KeyProvider {

    /**
     * Generate a random data encryption key (DEK) for envelope encryption.
     * Returns Base64-encoded 32-byte key (AES-256).
     */
    String generateDataKey();

    /**
     * Wrap a DEK using the key encryption key (KEK).
     * The KEK is typically the workspace encryption key.
     *
     * @param dek Base64-encoded plaintext DEK
     * @param kek Base64-encoded KEK
     * @return Base64-encoded wrapped DEK (ciphertext)
     */
    String wrapKey(String dek, String kek);

    /**
     * Unwrap a DEK using the key encryption key (KEK).
     *
     * @param wrappedDek Base64-encoded wrapped DEK (ciphertext)
     * @param kek Base64-encoded KEK
     * @return Base64-encoded plaintext DEK
     */
    String unwrapKey(String wrappedDek, String kek);

    /**
     * Get the algorithm/version identifier for this provider.
     * Used to detect format changes and support migration.
     */
    default String getAlgorithmVersion() {
        return "v1:local-pbkdf2";
    }

    /**
     * Optional: Rotate a wrapped DEK to a new KEK.
     * If not supported, returns empty.
     */
    default Optional<String> rotateWrappedKey(String wrappedDek, String oldKek, String newKek) {
        return Optional.empty();
    }
}

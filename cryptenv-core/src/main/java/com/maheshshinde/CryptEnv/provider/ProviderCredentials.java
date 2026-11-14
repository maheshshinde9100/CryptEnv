package com.maheshshinde.CryptEnv.provider;

import com.maheshshinde.CryptEnv.model.ProviderIntegration;

import java.util.Map;

/**
 * Decrypted provider credentials plus non-secret options, resolved from a stored
 * {@link ProviderIntegration} at call time. Never persisted or logged in plaintext.
 */
public class ProviderCredentials {

    private final String token;
    private final Map<String, String> config;

    public ProviderCredentials(String token, Map<String, String> config) {
        this.token = token;
        this.config = config != null ? config : Map.of();
    }

    public String token() {
        return token;
    }

    public Map<String, String> config() {
        return config;
    }

    public String config(String key) {
        return config.get(key);
    }

    public String config(String key, String defaultValue) {
        String value = config.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

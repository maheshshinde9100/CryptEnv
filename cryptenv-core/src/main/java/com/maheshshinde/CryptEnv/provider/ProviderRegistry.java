package com.maheshshinde.CryptEnv.provider;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves the {@link SecretProvider} adapter for a given provider id. */
@Component
public class ProviderRegistry {

    private final Map<String, SecretProvider> providers = new LinkedHashMap<>();

    public ProviderRegistry(List<SecretProvider> discovered) {
        for (SecretProvider provider : discovered) {
            providers.put(provider.providerId(), provider);
        }
    }

    public SecretProvider get(String providerId) {
        SecretProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new ProviderException("Unsupported provider: " + providerId);
        }
        return provider;
    }

    public boolean supports(String providerId) {
        return providers.containsKey(providerId);
    }

    public List<SecretProvider> all() {
        return List.copyOf(providers.values());
    }
}

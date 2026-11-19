package com.maheshshinde.CryptEnv.provider;

import java.util.List;

/**
 * SPI for pushing secrets into an external platform. Implementations must be stateless and
 * thread-safe — credential material is passed in per call.
 *
 * <p>Adapters are free-tier friendly: they use plain REST APIs with a user-supplied token.
 */
public interface SecretProvider {

    /** Stable identifier, e.g. {@code github}, {@code vercel}, {@code cloudflare}, {@code kubernetes}. */
    String providerId();

    /** Human-readable name shown in the dashboard. */
    String displayName();

    /**
     * Verify the supplied credentials and return a short account description
     * (e.g. the GitHub login / Vercel username). Implementations should perform a cheap
     * authenticated call and throw {@link ProviderException} on failure.
     */
    String checkHealth(ProviderCredentials credentials);

    /** List the push targets visible to these credentials. */
    List<ProviderTarget> listTargets(ProviderCredentials credentials);

    /** Create or update {@code key} in {@code target} with {@code value}. */
    void pushSecret(ProviderCredentials credentials, String target, String key, String value);

    /** Remove {@code key} from {@code target}. Implementations should be idempotent. */
    void deleteSecret(ProviderCredentials credentials, String target, String key);
}

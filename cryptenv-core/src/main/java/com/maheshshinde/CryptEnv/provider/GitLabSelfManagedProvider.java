package com.maheshshinde.CryptEnv.provider;

import org.springframework.stereotype.Component;

import java.util.List;

/** GitLab self-managed variant. Supply the instance URL in the non-secret {@code apiUrl} config. */
@Component
public class GitLabSelfManagedProvider implements SecretProvider {
    private final GitLabProvider delegate = new GitLabProvider();

    @Override public String providerId() { return "gitlab-self-managed"; }
    @Override public String displayName() { return "GitLab Self-Managed"; }
    @Override public String checkHealth(ProviderCredentials credentials) { return delegate.checkHealth(requireApiUrl(credentials)); }
    @Override public List<ProviderTarget> listTargets(ProviderCredentials credentials) { return delegate.listTargets(requireApiUrl(credentials)); }
    @Override public void pushSecret(ProviderCredentials credentials, String target, String key, String value) { delegate.pushSecret(requireApiUrl(credentials), target, key, value); }
    @Override public void deleteSecret(ProviderCredentials credentials, String target, String key) { delegate.deleteSecret(requireApiUrl(credentials), target, key); }

    private ProviderCredentials requireApiUrl(ProviderCredentials credentials) {
        if (credentials.config("apiUrl") == null || credentials.config("apiUrl").isBlank())
            throw new ProviderException("GitLab Self-Managed requires config.apiUrl, for example https://gitlab.example.com");
        return credentials;
    }
}

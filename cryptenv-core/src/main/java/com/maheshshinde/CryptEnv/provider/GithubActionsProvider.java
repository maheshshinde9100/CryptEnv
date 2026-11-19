package com.maheshshinde.CryptEnv.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.maheshshinde.CryptEnv.provider.ProviderHttp.enc;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.get;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.send;

/**
 * Pushes secrets to GitHub Actions repository secrets using libsodium sealed boxes.
 * Target: {@code owner/repo}.
 */
@Component
public class GithubActionsProvider implements SecretProvider {

    private static final String API = "https://api.github.com";

    @Override
    public String providerId() {
        return "github";
    }

    @Override
    public String displayName() {
        return "GitHub Actions";
    }

    @Override
    public String checkHealth(ProviderCredentials credentials) {
        JsonNode res = get(API + "/user", authHeaders(credentials));
        String login = res.path("login").asText(null);
        return login != null ? login : "connected";
    }

    @Override
    public List<ProviderTarget> listTargets(ProviderCredentials credentials) {
        JsonNode res = get(API + "/user/repos?per_page=100&sort=updated", authHeaders(credentials));
        List<ProviderTarget> targets = new ArrayList<>();
        for (JsonNode repo : res) {
            String fullName = repo.path("full_name").asText(null);
            if (fullName != null) {
                targets.add(new ProviderTarget(fullName, fullName, repo.path("html_url").asText(null)));
            }
        }
        return targets;
    }

    @Override
    public void pushSecret(ProviderCredentials credentials, String target, String key, String value) {
        String[] parts = splitRepo(target);
        JsonNode publicKey = get(
                API + "/repos/" + enc(parts[0]) + "/" + enc(parts[1]) + "/actions/secrets/public-key",
                authHeaders(credentials));

        String keyId = publicKey.path("key_id").asText(null);
        String base64Key = publicKey.path("key").asText(null);
        if (keyId == null || base64Key == null) {
            throw new ProviderException("GitHub did not return an Actions public key for " + target
                    + " (is Actions enabled?)");
        }

        byte[] sealed = SealedBox.seal(
                value.getBytes(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(base64Key));
        String encryptedValue = Base64.getEncoder().encodeToString(sealed);

        Map<String, Object> body = Map.of("encrypted_value", encryptedValue, "key_id", keyId);
        send(HttpMethod.PUT,
                API + "/repos/" + enc(parts[0]) + "/" + enc(parts[1]) + "/actions/secrets/" + enc(key),
                authHeaders(credentials), body);
    }

    @Override
    public void deleteSecret(ProviderCredentials credentials, String target, String key) {
        String[] parts = splitRepo(target);
        send(HttpMethod.DELETE,
                API + "/repos/" + enc(parts[0]) + "/" + enc(parts[1]) + "/actions/secrets/" + enc(key),
                authHeaders(credentials), null);
    }

    private String[] splitRepo(String target) {
        if (target == null || !target.contains("/")) {
            throw new ProviderException("GitHub target must be in owner/repo form, got: " + target);
        }
        int slash = target.indexOf('/');
        return new String[]{target.substring(0, slash), target.substring(slash + 1)};
    }

    private Map<String, String> authHeaders(ProviderCredentials credentials) {
        return Map.of(
                "Authorization", "Bearer " + credentials.token(),
                "Accept", "application/vnd.github+json",
                "X-GitHub-Api-Version", "2022-11-28");
    }
}

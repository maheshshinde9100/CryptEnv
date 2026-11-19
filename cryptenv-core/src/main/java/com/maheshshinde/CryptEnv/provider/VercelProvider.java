package com.maheshshinde.CryptEnv.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.maheshshinde.CryptEnv.provider.ProviderHttp.enc;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.get;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.send;

/**
 * Pushes secrets to Vercel project environment variables.
 * Config: optional {@code teamId}.
 * Target: project id or name.
 */
@Component
public class VercelProvider implements SecretProvider {

    private static final String API = "https://api.vercel.com";
    private static final List<String> DEFAULT_TARGETS = List.of("production", "preview", "development");

    @Override
    public String providerId() {
        return "vercel";
    }

    @Override
    public String displayName() {
        return "Vercel";
    }

    @Override
    public String checkHealth(ProviderCredentials credentials) {
        JsonNode res = get(teamUrl("/v2/user", credentials), authHeaders(credentials));
        JsonNode user = res.path("user");
        String username = user.path("username").asText(null);
        String email = user.path("email").asText(null);
        return username != null ? username : (email != null ? email : "connected");
    }

    @Override
    public List<ProviderTarget> listTargets(ProviderCredentials credentials) {
        JsonNode res = get(withQuery("/v9/projects", "limit=100", credentials), authHeaders(credentials));
        List<ProviderTarget> targets = new ArrayList<>();
        for (JsonNode project : res.path("projects")) {
            String id = project.path("id").asText(null);
            String name = project.path("name").asText(id);
            if (id != null) {
                targets.add(new ProviderTarget(id, name, "https://vercel.com/" + enc(name)));
            }
        }
        return targets;
    }

    @Override
    public void pushSecret(ProviderCredentials credentials, String target, String key, String value) {
        Map<String, Object> body = Map.of(
                "key", key,
                "value", value,
                "type", "encrypted",
                "target", DEFAULT_TARGETS);
        // upsert=true creates the env var or updates it when the key already exists
        String url = withQuery("/v10/projects/" + enc(target) + "/env", "upsert=true", credentials);
        send(HttpMethod.POST, url, authHeaders(credentials), body);
    }

    @Override
    public void deleteSecret(ProviderCredentials credentials, String target, String key) {
        String envId = findEnvId(credentials, target, key);
        if (envId == null) {
            return; // already absent — idempotent
        }
        send(HttpMethod.DELETE, teamUrl("/v9/projects/" + enc(target) + "/env/" + enc(envId), credentials),
                authHeaders(credentials), null);
    }

    private String findEnvId(ProviderCredentials credentials, String target, String key) {
        JsonNode res = get(teamUrl("/v10/projects/" + enc(target) + "/env", credentials), authHeaders(credentials));
        for (JsonNode env : res.path("envs")) {
            if (key.equals(env.path("key").asText())) {
                return env.path("id").asText(null);
            }
        }
        return null;
    }

    private Map<String, String> authHeaders(ProviderCredentials credentials) {
        return Map.of("Authorization", "Bearer " + credentials.token());
    }

    private String teamUrl(String path, ProviderCredentials credentials) {
        String teamId = credentials.config("teamId");
        return teamId == null ? API + path : API + path + (path.contains("?") ? "&" : "?") + "teamId=" + enc(teamId);
    }

    private String withQuery(String path, String query, ProviderCredentials credentials) {
        String teamId = credentials.config("teamId");
        String full = query;
        if (teamId != null) {
            full = query + "&teamId=" + enc(teamId);
        }
        return API + path + "?" + full;
    }
}

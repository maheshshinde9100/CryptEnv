package com.maheshshinde.CryptEnv.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.maheshshinde.CryptEnv.provider.ProviderHttp.enc;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.get;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.getOrNull;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.send;

/** GitLab project CI/CD variables. The optional apiUrl config also supports GitLab self-managed. */
@Component
public class GitLabProvider implements SecretProvider {
    private static final String CLOUD_API = "https://gitlab.com/api/v4";

    @Override public String providerId() { return "gitlab"; }
    @Override public String displayName() { return "GitLab"; }

    @Override
    public String checkHealth(ProviderCredentials credentials) {
        JsonNode user = get(api(credentials) + "/user", headers(credentials));
        return user.path("username").asText(user.path("name").asText("connected"));
    }

    @Override
    public List<ProviderTarget> listTargets(ProviderCredentials credentials) {
        JsonNode projects = get(api(credentials) + "/projects?membership=true&simple=true&per_page=100", headers(credentials));
        List<ProviderTarget> targets = new ArrayList<>();
        for (JsonNode project : projects) {
            String id = project.path("id").asText(null);
            String path = project.path("path_with_namespace").asText(id);
            if (id != null) targets.add(new ProviderTarget(id, path, project.path("web_url").asText(null)));
        }
        return targets;
    }

    @Override
    public void pushSecret(ProviderCredentials credentials, String target, String key, String value) {
        String url = variableUrl(credentials, target, key);
        Map<String, Object> body = Map.of("key", key, "value", value, "masked", true);
        if (getOrNull(url, headers(credentials)) == null) {
            send(HttpMethod.POST, api(credentials) + "/projects/" + enc(target) + "/variables", headers(credentials), body);
        } else {
            send(HttpMethod.PUT, url, headers(credentials), Map.of("value", value, "masked", true));
        }
    }

    @Override
    public void deleteSecret(ProviderCredentials credentials, String target, String key) {
        String url = variableUrl(credentials, target, key);
        if (getOrNull(url, headers(credentials)) != null) send(HttpMethod.DELETE, url, headers(credentials), null);
    }

    private String variableUrl(ProviderCredentials credentials, String target, String key) {
        return api(credentials) + "/projects/" + enc(target) + "/variables/" + enc(key);
    }

    private String api(ProviderCredentials credentials) {
        String configured = credentials.config("apiUrl");
        if (configured == null || configured.isBlank()) return CLOUD_API;
        return configured.replaceAll("/+$", "") + (configured.endsWith("/api/v4") ? "" : "/api/v4");
    }

    private Map<String, String> headers(ProviderCredentials credentials) {
        return Map.of("PRIVATE-TOKEN", credentials.token());
    }
}

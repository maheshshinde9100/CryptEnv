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
 * Pushes secrets to Cloudflare Workers secrets and Pages environment variables.
 * Config: required {@code accountId}.
 * Target: {@code worker:<scriptName>} or {@code pages:<projectName>}.
 */
@Component
public class CloudflareProvider implements SecretProvider {

    private static final String API = "https://api.cloudflare.com/client/v4";

    private static final String WORKER_PREFIX = "worker:";
    private static final String PAGES_PREFIX = "pages:";

    @Override
    public String providerId() {
        return "cloudflare";
    }

    @Override
    public String displayName() {
        return "Cloudflare Workers / Pages";
    }

    @Override
    public String checkHealth(ProviderCredentials credentials) {
        JsonNode res = get(accountUrl(credentials), authHeaders(credentials));
        JsonNode result = res.path("result");
        String name = result.path("name").asText(null);
        return name != null ? name : "connected";
    }

    @Override
    public List<ProviderTarget> listTargets(ProviderCredentials credentials) {
        String accountId = requireAccountId(credentials);
        List<ProviderTarget> targets = new ArrayList<>();

        JsonNode workers = get(API + "/accounts/" + enc(accountId) + "/workers/scripts", authHeaders(credentials));
        for (JsonNode worker : workers.path("result")) {
            String id = worker.path("id").asText(null);
            if (id != null) {
                targets.add(new ProviderTarget(WORKER_PREFIX + id, "Worker · " + id,
                        "https://dash.cloudflare.com/" + enc(accountId) + "/workers/services/view/" + enc(id)));
            }
        }

        JsonNode pages = get(API + "/accounts/" + enc(accountId) + "/pages/projects", authHeaders(credentials));
        for (JsonNode project : pages.path("result")) {
            String name = project.path("name").asText(null);
            if (name != null) {
                targets.add(new ProviderTarget(PAGES_PREFIX + name, "Pages · " + name,
                        "https://dash.cloudflare.com/" + enc(accountId) + "/pages/view/" + enc(name)));
            }
        }
        return targets;
    }

    @Override
    public void pushSecret(ProviderCredentials credentials, String target, String key, String value) {
        if (!pushWorkerSecret(credentials, target, key, value)) {
            pushPagesSecret(credentials, target, key, value);
        }
    }

    private boolean pushWorkerSecret(ProviderCredentials credentials, String target, String key, String value) {
        if (!target.startsWith(WORKER_PREFIX)) {
            return false;
        }
        String script = target.substring(WORKER_PREFIX.length());
        String accountId = requireAccountId(credentials);
        String url = API + "/accounts/" + enc(accountId) + "/workers/scripts/" + enc(script) + "/secrets";
        send(HttpMethod.PUT, url, authHeaders(credentials),
                Map.of("name", key, "text", value, "type", "secret_text"));
        return true;
    }

    private void pushPagesSecret(ProviderCredentials credentials, String target, String key, String value) {
        if (!target.startsWith(PAGES_PREFIX)) {
            throw new ProviderException("Unsupported Cloudflare target: " + target);
        }
        patchPagesEnv(credentials, target.substring(PAGES_PREFIX.length()), Map.of(key, Map.of("value", value)));
    }

    @Override
    public void deleteSecret(ProviderCredentials credentials, String target, String key) {
        if (target.startsWith(WORKER_PREFIX)) {
            String script = target.substring(WORKER_PREFIX.length());
            String accountId = requireAccountId(credentials);
            String url = API + "/accounts/" + enc(accountId) + "/workers/scripts/" + enc(script) + "/secrets/" + enc(key);
            send(HttpMethod.DELETE, url, authHeaders(credentials), null);
            return;
        }
        if (target.startsWith(PAGES_PREFIX)) {
            // Setting an env var to null removes it in the Pages API
            patchPagesEnv(credentials, target.substring(PAGES_PREFIX.length()), java.util.Collections.singletonMap(key, null));
            return;
        }
        throw new ProviderException("Unsupported Cloudflare target: " + target);
    }

    private void patchPagesEnv(ProviderCredentials credentials, String project, Map<String, Object> envVars) {
        String accountId = requireAccountId(credentials);
        String url = API + "/accounts/" + enc(accountId) + "/pages/projects/" + enc(project);
        Map<String, Object> body = Map.of("deployment_configs", Map.of(
                "production", Map.of("env_vars", envVars),
                "preview", Map.of("env_vars", envVars)));
        send(HttpMethod.PATCH, url, authHeaders(credentials), body);
    }

    private String requireAccountId(ProviderCredentials credentials) {
        String accountId = credentials.config("accountId");
        if (accountId == null || accountId.isBlank()) {
            throw new ProviderException("Cloudflare integration is missing the accountId config");
        }
        return accountId;
    }

    private Map<String, String> authHeaders(ProviderCredentials credentials) {
        return Map.of("Authorization", "Bearer " + credentials.token());
    }

    private String accountUrl(ProviderCredentials credentials) {
        return API + "/accounts/" + enc(requireAccountId(credentials));
    }
}

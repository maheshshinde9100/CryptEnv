package com.maheshshinde.CryptEnv.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.maheshshinde.CryptEnv.provider.ProviderHttp.enc;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.get;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.getOrNull;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.send;

/**
 * Pushes secrets to a Kubernetes {@code Opaque} Secret (create-or-merge).
 * Config: required {@code apiUrl}, required {@code namespace}, optional {@code secretName}
 * (default {@code cryptenv-secrets}).
 * Target: the Secret name.
 */
@Component
public class KubernetesProvider implements SecretProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_SECRET_NAME = "cryptenv-secrets";

    @Override
    public String providerId() {
        return "kubernetes";
    }

    @Override
    public String displayName() {
        return "Kubernetes Secret";
    }

    @Override
    public String checkHealth(ProviderCredentials credentials) {
        JsonNode res = get(baseUrl(credentials) + "/api/v1/namespaces/" + enc(requireNamespace(credentials)),
                authHeaders(credentials));
        String name = res.path("metadata").path("name").asText(null);
        return name != null ? "namespace " + name : "connected";
    }

    @Override
    public List<ProviderTarget> listTargets(ProviderCredentials credentials) {
        JsonNode res = get(baseUrl(credentials) + "/api/v1/namespaces/" + enc(requireNamespace(credentials)) + "/secrets",
                authHeaders(credentials));
        List<ProviderTarget> targets = new ArrayList<>();
        for (JsonNode item : res.path("items")) {
            String name = item.path("metadata").path("name").asText(null);
            if (name != null) {
                targets.add(new ProviderTarget(name, name, null));
            }
        }
        return targets;
    }

    @Override
    public void pushSecret(ProviderCredentials credentials, String target, String key, String value) {
        String secretName = (target == null || target.isBlank()) ? defaultSecretName(credentials) : target;
        String url = secretUrl(credentials, secretName);
        JsonNode existing = getOrNull(url, authHeaders(credentials));
        String encodedValue = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

        Map<String, String> data = new LinkedHashMap<>();
        if (existing != null) {
            JsonNode dataNode = existing.path("data");
            dataNode.fields().forEachRemaining(entry -> data.put(entry.getKey(), entry.getValue().asText()));
        }
        data.put(key, encodedValue);

        ObjectNode body = MAPPER.createObjectNode();
        body.put("apiVersion", "v1");
        body.put("kind", "Secret");
        ObjectNode metadata = body.putObject("metadata");
        metadata.put("name", secretName);
        metadata.put("namespace", requireNamespace(credentials));
        ObjectNode dataNode = body.putObject("data");
        data.forEach(dataNode::put);
        body.put("type", "Opaque");

        if (existing == null) {
            send(HttpMethod.POST, collectionUrl(credentials), authHeaders(credentials), body);
        } else {
            send(HttpMethod.PUT, url, authHeaders(credentials), body);
        }
    }

    @Override
    public void deleteSecret(ProviderCredentials credentials, String target, String key) {
        String secretName = (target == null || target.isBlank()) ? defaultSecretName(credentials) : target;
        String url = secretUrl(credentials, secretName);
        JsonNode existing = getOrNull(url, authHeaders(credentials));
        if (existing == null) {
            return; // already absent — idempotent
        }

        Map<String, String> data = new LinkedHashMap<>();
        existing.path("data").fields().forEachRemaining(entry -> data.put(entry.getKey(), entry.getValue().asText()));
        if (data.remove(key) == null) {
            return; // key not present — idempotent
        }

        ObjectNode body = MAPPER.createObjectNode();
        body.put("apiVersion", "v1");
        body.put("kind", "Secret");
        ObjectNode metadata = body.putObject("metadata");
        metadata.put("name", secretName);
        metadata.put("namespace", requireNamespace(credentials));
        ObjectNode dataNode = body.putObject("data");
        data.forEach(dataNode::put);
        body.put("type", "Opaque");

        send(HttpMethod.PUT, url, authHeaders(credentials), body);
    }

    private String baseUrl(ProviderCredentials credentials) {
        String apiUrl = credentials.config("apiUrl");
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new ProviderException("Kubernetes integration is missing the apiUrl config");
        }
        return apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }

    private String requireNamespace(ProviderCredentials credentials) {
        String namespace = credentials.config("namespace");
        if (namespace == null || namespace.isBlank()) {
            throw new ProviderException("Kubernetes integration is missing the namespace config");
        }
        return namespace;
    }

    private String defaultSecretName(ProviderCredentials credentials) {
        return credentials.config("secretName", DEFAULT_SECRET_NAME);
    }

    private String collectionUrl(ProviderCredentials credentials) {
        return baseUrl(credentials) + "/api/v1/namespaces/" + enc(requireNamespace(credentials)) + "/secrets";
    }

    private String secretUrl(ProviderCredentials credentials, String secretName) {
        return collectionUrl(credentials) + "/" + enc(secretName);
    }

    private Map<String, String> authHeaders(ProviderCredentials credentials) {
        return Map.of("Authorization", "Bearer " + credentials.token());
    }
}

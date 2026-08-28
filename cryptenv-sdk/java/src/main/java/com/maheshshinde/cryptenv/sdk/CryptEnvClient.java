package com.maheshshinde.cryptenv.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CryptEnvClient {

    private final CryptEnvConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private boolean initialized = false;
    private String token;
    private String workspaceId;
    private Long environmentId;

    private Map<String, String> encryptedSecretsMap = new ConcurrentHashMap<>();
    private Map<String, String> plaintextCache = new ConcurrentHashMap<>();

    public CryptEnvClient() {
        this(new CryptEnvConfig());
    }

    public CryptEnvClient(CryptEnvConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.token = config.getToken();
        this.workspaceId = config.getWorkspaceId();
    }

    public static CryptEnvClient create(CryptEnvConfig config) throws IOException, InterruptedException {
        CryptEnvClient client = new CryptEnvClient(config);
        client.init();
        return client;
    }

    public static CryptEnvClient create() throws IOException, InterruptedException {
        return create(new CryptEnvConfig());
    }

    public void init() throws IOException, InterruptedException {
        boolean hasPasswordAuth = config.getEmail() != null && config.getPassword() != null;
        boolean hasApiKeyAuth = config.getApiKey() != null;
        boolean hasTokenAuth = this.token != null;

        if (!hasPasswordAuth && !hasApiKeyAuth && !hasTokenAuth) {
            throw new RuntimeException("CryptEnv SDK: missing credentials. Set CRYPTENV_EMAIL and CRYPTENV_PASSWORD, or CRYPTENV_API_KEY, or CRYPTENV_TOKEN in .env.");
        }

        if (hasPasswordAuth) {
            authenticateWithPassword();
        } else if (hasTokenAuth) {
            authenticateWithToken();
        } else {
            authenticateWithApiKey();
        }

        this.initialized = true;

        if (this.workspaceId == null) {
            throw new RuntimeException("CryptEnv SDK: no workspace available. Set CRYPTENV_WORKSPACE_ID or CRYPTENV_WORKSPACE.");
        }

        refresh();
    }

    private void authenticateWithPassword() throws IOException, InterruptedException {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", config.getEmail());
        payload.put("password", config.getPassword());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/api/sdk/login"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Login failed: " + response.body());
        }

        Map<String, Object> data = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        this.token = (String) data.get("token");

        List<Map<String, Object>> workspaces = (List<Map<String, Object>>) data.get("workspaces");

        String targetWorkspaceName = config.getWorkspace();
        String targetEnvName = config.getEnvironment();

        if (this.workspaceId == null && workspaces != null && !workspaces.isEmpty()) {
            if (targetWorkspaceName != null) {
                for (Map<String, Object> ws : workspaces) {
                    String wsName = (String) ws.get("name");
                    if (targetWorkspaceName.equals(wsName)) {
                        this.workspaceId = String.valueOf(ws.get("id"));
                        this.environmentId = findEnvironmentId(ws, targetEnvName);
                        break;
                    }
                }
            }
            if (this.workspaceId == null) {
                for (Map<String, Object> ws : workspaces) {
                    Boolean hasKey = (Boolean) ws.get("hasEncryptionKey");
                    if (Boolean.TRUE.equals(hasKey)) {
                        this.workspaceId = String.valueOf(ws.get("id"));
                        this.environmentId = findEnvironmentId(ws, targetEnvName);
                        break;
                    }
                }
            }
            if (this.workspaceId == null) {
                Map<String, Object> ws0 = workspaces.get(0);
                this.workspaceId = String.valueOf(ws0.get("id"));
                this.environmentId = findEnvironmentId(ws0, targetEnvName);
            }
        } else if (workspaces != null && this.workspaceId != null) {
            for (Map<String, Object> ws : workspaces) {
                if (this.workspaceId.equals(String.valueOf(ws.get("id")))) {
                    this.environmentId = findEnvironmentId(ws, targetEnvName);
                    break;
                }
            }
        }
    }

    private Long findEnvironmentId(Map<String, Object> workspace, String envName) {
        if (envName == null) return null;
        List<Map<String, Object>> envs = (List<Map<String, Object>>) workspace.get("environments");
        if (envs == null) return null;
        for (Map<String, Object> e : envs) {
            if (envName.equalsIgnoreCase((String) e.get("name"))) {
                Object id = e.get("id");
                if (id instanceof Number) return ((Number) id).longValue();
                try { return Long.parseLong(id.toString()); } catch (Exception ex) { return null; }
            }
        }
        return null;
    }

    private void authenticateWithApiKey() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/api/workspaces"))
                .header("Accept", "application/json")
                .header("X-API-Key", config.getApiKey())
                .GET();

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && this.workspaceId == null) {
            List<Map<String, Object>> workspaces = objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
            if (!workspaces.isEmpty()) {
                this.workspaceId = String.valueOf(workspaces.get(0).get("id"));
            }
        }
    }

    private void authenticateWithToken() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/api/workspaces"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + this.token)
                .GET();

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && this.workspaceId == null) {
            List<Map<String, Object>> workspaces = objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
            if (!workspaces.isEmpty()) {
                this.workspaceId = String.valueOf(workspaces.get(0).get("id"));
            }
        }
    }

    public int refresh() throws IOException, InterruptedException {
        if (!initialized) {
            throw new RuntimeException("CryptEnv SDK not initialized. Call init() first.");
        }
        if (workspaceId == null) {
            throw new RuntimeException("No workspace selected. Set CRYPTENV_WORKSPACE_ID or CRYPTENV_WORKSPACE.");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/api/sdk/workspaces/" + workspaceId + "/encrypted-secrets-map"))
                .header("Accept", "application/json")
                .GET();

        if (this.token != null) {
            builder.header("Authorization", "Bearer " + this.token);
        } else if (config.getApiKey() != null) {
            builder.header("X-API-Key", config.getApiKey());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to refresh secrets: " + response.body());
        }

        Map<String, String> map = objectMapper.readValue(response.body(), new TypeReference<Map<String, String>>() {});
        this.encryptedSecretsMap.clear();
        this.plaintextCache.clear();

        if (map != null) {
            this.encryptedSecretsMap.putAll(map);
        }

        return this.encryptedSecretsMap.size();
    }

    public List<String> listKeys() {
        if (!initialized) {
            throw new RuntimeException("CryptEnv SDK not initialized. Call init() first.");
        }
        return new ArrayList<>(this.encryptedSecretsMap.keySet());
    }

    public SecretValue getSecret(String key) throws IOException, InterruptedException {
        if (!initialized) {
            throw new RuntimeException("CryptEnv SDK not initialized. Call init() first.");
        }
        if (key == null) return null;

        if (plaintextCache.containsKey(key)) {
            return new SecretValue(key, plaintextCache.get(key));
        }

        String enc = encryptedSecretsMap.get(key);
        if (enc == null) {
            enc = fetchSingleEncryptedSecret(key);
            if (enc != null) {
                encryptedSecretsMap.put(key, enc);
            }
        }

        if (enc == null) {
            return null;
        }

        String masterKey = config.getMasterKey();
        if (masterKey == null) {
            throw new RuntimeException("CryptEnv SDK: workspace encryption key not configured. Set CRYPTENV_MASTER_KEY or CRYPTENV_WORKSPACE_ENCRYPTION_KEY.");
        }

        String plain = CryptoUtils.decryptWithKey(enc, masterKey);
        plaintextCache.put(key, plain);
        return new SecretValue(key, plain);
    }

    private String fetchSingleEncryptedSecret(String key) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/api/sdk/secrets/" + encodePath(key)))
                .header("Accept", "application/json")
                .GET();

        if (this.token != null) {
            builder.header("Authorization", "Bearer " + this.token);
        } else if (config.getApiKey() != null) {
            builder.header("X-API-Key", config.getApiKey());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        Map<String, Object> data = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        Object encVal = data.get("encryptedValue");
        if (encVal == null) encVal = data.get("value");
        return encVal != null ? encVal.toString() : null;
    }

    private static String encodePath(String s) {
        try { return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name())
                .replace("+", "%20"); }
        catch (Exception e) { return s; }
    }

    public String get(String key) {
        if (!initialized) {
            throw new RuntimeException("CryptEnv SDK not initialized. Call init() first.");
        }
        if (key == null) return null;

        if (plaintextCache.containsKey(key)) {
            return plaintextCache.get(key);
        }

        String enc = encryptedSecretsMap.get(key);
        if (enc == null) {
            return null;
        }

        String masterKey = config.getMasterKey();
        if (masterKey == null) {
            throw new RuntimeException("CryptEnv SDK: workspace encryption key not configured. Set CRYPTENV_MASTER_KEY or CRYPTENV_WORKSPACE_ENCRYPTION_KEY.");
        }

        String plain = CryptoUtils.decryptWithKey(enc, masterKey);
        plaintextCache.put(key, plain);
        return plain;
    }

    public Map<String, String> getAll() {
        if (!initialized) {
            throw new RuntimeException("CryptEnv SDK not initialized. Call init() first.");
        }
        Map<String, String> result = new HashMap<>();
        for (String key : encryptedSecretsMap.keySet()) {
            result.put(key, get(key));
        }
        return result;
    }

    public boolean isInitialized() { return initialized; }
    public String getWorkspaceId() { return workspaceId; }
    public Long getEnvironmentId() { return environmentId; }
    public CryptEnvConfig getConfig() { return config; }

    public static class SecretValue {
        private final String key;
        private final String value;

        public SecretValue(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() { return key; }
        public String getValue() { return value; }

        @Override
        public String toString() { return value == null ? "" : value; }
    }
}

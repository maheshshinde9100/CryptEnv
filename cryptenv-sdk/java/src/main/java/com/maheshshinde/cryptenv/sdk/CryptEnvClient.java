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
    
    // key -> base64 encrypted value
    private Map<String, String> encryptedSecretsMap = new ConcurrentHashMap<>();
    
    // key -> plaintext value
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
            throw new RuntimeException("CryptEnv SDK: no workspace available. Set CRYPTENV_WORKSPACE_ID.");
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

        if (this.workspaceId == null) {
            List<Map<String, Object>> workspaces = (List<Map<String, Object>>) data.get("workspaces");
            if (workspaces != null && !workspaces.isEmpty()) {
                for (Map<String, Object> ws : workspaces) {
                    Boolean hasKey = (Boolean) ws.get("hasEncryptionKey");
                    if (Boolean.TRUE.equals(hasKey)) {
                        this.workspaceId = String.valueOf(ws.get("id"));
                        break;
                    }
                }
                if (this.workspaceId == null) {
                    this.workspaceId = String.valueOf(workspaces.get(0).get("id"));
                }
            }
        }
    }

    private void authenticateWithApiKey() throws IOException, InterruptedException {
        // Mock token fetching or use API key directly in further requests.
        // For simplicity, we just fetch workspaces.
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
            throw new RuntimeException("No workspace selected. Set CRYPTENV_WORKSPACE_ID.");
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

        if (config.getMasterKey() == null) {
            throw new RuntimeException("CryptEnv SDK: master key not configured. Set CRYPTENV_MASTER_KEY.");
        }

        String plain = CryptoUtils.decryptWithKey(enc, config.getMasterKey());
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
}

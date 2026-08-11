package com.maheshshinde.cryptenv.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CryptEnvClient {

    private final String baseUrl;
    private final String apiKey;
    private final String jwtToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CryptEnvClient(String apiKey) {
        this(System.getenv("CRYPTENV_API_URL") != null ? System.getenv("CRYPTENV_API_URL") : "https://cryptenv-backend.onrender.com", apiKey, null);
    }

    public CryptEnvClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, null);
    }

    public CryptEnvClient(String baseUrl, String apiKey, String jwtToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.jwtToken = jwtToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, String> getSecrets() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/secrets"))
                .header("Accept", "application/json")
                .GET();

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("X-API-Key", apiKey);
        } else if (jwtToken != null && !jwtToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("CryptEnv API call failed with status: " + response.statusCode() + ", body: " + response.body());
        }

        List<Map<String, Object>> secretsList = objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
        Map<String, String> resultMap = new HashMap<>();
        for (Map<String, Object> secret : secretsList) {
            String key = (String) secret.get("key");
            String value = (String) secret.get("value");
            if (key != null && value != null) {
                resultMap.put(key, value);
            }
        }
        return resultMap;
    }

    public String getSecret(String key) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/secrets/" + key))
                .header("Accept", "application/json")
                .GET();

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("X-API-Key", apiKey);
        } else if (jwtToken != null && !jwtToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("CryptEnv API call failed with status: " + response.statusCode() + ", key: " + key);
        }

        Map<String, Object> secretMap = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        return (String) secretMap.get("value");
    }
}

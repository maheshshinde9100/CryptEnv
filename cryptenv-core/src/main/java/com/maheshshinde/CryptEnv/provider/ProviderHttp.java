package com.maheshshinde.CryptEnv.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Thin shared HTTP helper for provider adapters. Uses the JDK HTTP client with short timeouts
 * (provider calls run inside the sync worker, which must never hang).
 */
public final class ProviderHttp {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final RestClient CLIENT = buildClient();

    private ProviderHttp() {
    }

    private static RestClient buildClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory).build();
    }

    /** Percent-encode a path segment / query value. */
    public static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static JsonNode get(String url, Map<String, String> headers) {
        return exchange(HttpMethod.GET, url, headers, null);
    }

    /** GET that returns {@code null} instead of throwing when the resource is absent (HTTP 404). */
    public static JsonNode getOrNull(String url, Map<String, String> headers) {
        try {
            return get(url, headers);
        } catch (ProviderException e) {
            if (e.getCause() instanceof RestClientResponseException rce
                    && rce.getStatusCode().value() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static JsonNode send(HttpMethod method, String url, Map<String, String> headers, Object body) {
        return exchange(method, url, headers, body);
    }

    private static JsonNode exchange(HttpMethod method, String url, Map<String, String> headers, Object body) {
        try {
            RestClient.RequestBodySpec spec = CLIENT.method(method)
                    .uri(url)
                    .headers(h -> {
                        if (headers != null) {
                            headers.forEach(h::set);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            RestClient.ResponseSpec responseSpec = body != null
                    ? spec.body(body).retrieve()
                    : spec.retrieve();
            String raw = responseSpec.body(String.class);
            if (raw == null || raw.isBlank()) {
                return MAPPER.createObjectNode();
            }
            return MAPPER.readTree(raw);
        } catch (RestClientResponseException e) {
            throw new ProviderException(
                    "Provider responded " + e.getStatusCode().value() + ": " + truncate(e.getResponseBodyAsString()),
                    e);
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("Provider request failed: " + e.getMessage(), e);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 300 ? compact.substring(0, 300) : compact;
    }
}

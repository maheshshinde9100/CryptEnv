package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.DomainEvent;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.WebhookDelivery;
import com.maheshshinde.CryptEnv.model.WebhookEndpoint;
import com.maheshshinde.CryptEnv.repository.DomainEventRepository;
import com.maheshshinde.CryptEnv.repository.WebhookDeliveryRepository;
import com.maheshshinde.CryptEnv.repository.WebhookEndpointRepository;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import com.maheshshinde.CryptEnv.security.SsrfGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Outbound webhooks (§10): endpoint management, HMAC-SHA256-signed delivery, retries with
 * exponential backoff, a delivery log and manual redelivery.
 * <p>
 * Every outbound request is validated by {@link SsrfGuard} and redirects are never followed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final int BASE_BACKOFF_SECONDS = 30;
    private static final int MAX_BACKOFF_SECONDS = 1800;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final DomainEventRepository eventRepository;
    private final EncryptionService encryptionService;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;

    @Value("${cryptenv.webhooks.enabled:true}")
    boolean webhooksEnabled;

    private final SecureRandom random = new SecureRandom();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    // ------------------------------------------------------------------ endpoints

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEndpoints() {
        User user = securityService.getCurrentUser();
        return endpointRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toEndpointView)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createEndpoint(String url, String description, Long workspaceId, List<String> eventTypes) {
        User user = securityService.getCurrentUser();
        validateUrl(url);

        String secret = generateSecret();
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .user(user)
                .workspaceId(workspaceId)
                .url(url.trim())
                .description(description)
                .secretEncrypted(encryptionService.encryptWithMasterKey(secret))
                .eventTypes(normalizeEventTypes(eventTypes))
                .active(true)
                .build();

        WebhookEndpoint saved = endpointRepository.save(endpoint);
        Map<String, Object> view = toEndpointView(saved);
        // The signing secret is shown exactly once, at creation time.
        view.put("secret", secret);
        return view;
    }

    @Transactional
    public Map<String, Object> updateEndpoint(Long id, String url, String description,
                                              List<String> eventTypes, Boolean active) {
        WebhookEndpoint endpoint = ownedEndpoint(id);
        if (url != null && !url.isBlank() && !url.equals(endpoint.getUrl())) {
            validateUrl(url);
            endpoint.setUrl(url.trim());
        }
        if (description != null) {
            endpoint.setDescription(description);
        }
        if (eventTypes != null) {
            endpoint.setEventTypes(normalizeEventTypes(eventTypes));
        }
        if (active != null) {
            endpoint.setActive(active);
        }
        return toEndpointView(endpointRepository.save(endpoint));
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        endpointRepository.delete(ownedEndpoint(id));
    }

    @Transactional
    public Map<String, Object> rotateSecret(Long id) {
        WebhookEndpoint endpoint = ownedEndpoint(id);
        String secret = generateSecret();
        endpoint.setSecretEncrypted(encryptionService.encryptWithMasterKey(secret));
        WebhookEndpoint saved = endpointRepository.save(endpoint);
        Map<String, Object> view = toEndpointView(saved);
        view.put("secret", secret);
        return view;
    }

    /** Sends a signed test payload synchronously and reports the result. */
    @Transactional
    public Map<String, Object> testEndpoint(Long id) {
        WebhookEndpoint endpoint = ownedEndpoint(id);
        WebhookDelivery delivery = WebhookDelivery.builder()
                .endpoint(endpoint)
                .userId(endpoint.getUser().getId())
                .eventType("webhook.test")
                .url(endpoint.getUrl())
                .status(WebhookDelivery.Status.PENDING)
                .attempts(0)
                .maxAttempts(1)
                .nextAttemptAt(LocalDateTime.now())
                .build();
        WebhookDelivery saved = deliveryRepository.save(delivery);
        attemptDelivery(saved);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deliveryId", saved.getId());
        result.put("status", saved.getStatus().name());
        result.put("responseCode", saved.getResponseCode());
        result.put("error", saved.getError());
        return result;
    }

    // ------------------------------------------------------------------ deliveries

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDeliveries(Long endpointId) {
        User user = securityService.getCurrentUser();
        List<WebhookDelivery> deliveries = endpointId != null
                ? deliveryRepository.findByEndpointIdOrderByCreatedAtDesc(endpointId).stream()
                        .filter(d -> ownedBy(d, user.getId()))
                        .collect(Collectors.toList())
                : deliveryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return deliveries.stream().map(this::toDeliveryView).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> redeliver(Long deliveryId) {
        User user = securityService.getCurrentUser();
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .filter(d -> ownedBy(d, user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + deliveryId));
        delivery.setStatus(WebhookDelivery.Status.PENDING);
        delivery.setAttempts(0);
        delivery.setError(null);
        delivery.setNextAttemptAt(LocalDateTime.now());
        return toDeliveryView(deliveryRepository.save(delivery));
    }

    /** Creates delivery rows for every active endpoint that subscribes to this event type. */
    @Transactional
    public List<Long> fanOut(DomainEvent event) {
        if (!webhooksEnabled || event.getUserId() == null) {
            return List.of();
        }
        List<WebhookEndpoint> endpoints = new ArrayList<>(endpointRepository.findByUserIdAndActiveTrue(event.getUserId()));
        if (event.getWorkspaceId() != null) {
            endpoints.addAll(endpointRepository.findByWorkspaceIdAndActiveTrue(event.getWorkspaceId()));
        }

        List<Long> ids = new ArrayList<>();
        for (WebhookEndpoint endpoint : endpoints.stream().distinct().collect(Collectors.toList())) {
            if (!subscribes(endpoint, event.getType())) {
                continue;
            }
            WebhookDelivery delivery = WebhookDelivery.builder()
                    .endpoint(endpoint)
                    .eventId(event.getId())
                    .userId(event.getUserId())
                    .eventType(event.getType())
                    .url(endpoint.getUrl())
                    .status(WebhookDelivery.Status.PENDING)
                    .attempts(0)
                    .maxAttempts(5)
                    .nextAttemptAt(LocalDateTime.now())
                    .build();
            ids.add(deliveryRepository.save(delivery).getId());
        }
        return ids;
    }

    @Transactional
    public Optional<Long> claimNextDueDelivery() {
        List<WebhookDelivery> due = deliveryRepository.findDueDeliveries(LocalDateTime.now());
        if (due.isEmpty()) {
            return Optional.empty();
        }
        WebhookDelivery delivery = due.get(0);
        delivery.setStatus(WebhookDelivery.Status.PENDING);
        delivery.setAttempts(delivery.getAttempts() + 1);
        return Optional.of(deliveryRepository.save(delivery).getId());
    }

    @Transactional
    public void executeDelivery(Long deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        attemptDelivery(delivery);
        deliveryRepository.save(delivery);
    }

    /** Performs one signed delivery attempt and updates retry state. */
    private void attemptDelivery(WebhookDelivery delivery) {
        WebhookEndpoint endpoint = delivery.getEndpoint();
        String secret = encryptionService.decryptWithMasterKey(endpoint.getSecretEncrypted());
        String body = buildPayload(delivery);
        long timestamp = Instant.now().getEpochSecond();
        String signature = sign(secret, timestamp, body);

        try {
            SsrfGuard.assertAllowed(delivery.getUrl());
            HttpRequest request = HttpRequest.newBuilder(URI.create(delivery.getUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-CryptEnv-Event", delivery.getEventType())
                    .header("X-CryptEnv-Delivery", String.valueOf(delivery.getId()))
                    .header("X-CryptEnv-Timestamp", String.valueOf(timestamp))
                    .header("X-CryptEnv-Signature", "sha256=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            delivery.setResponseCode(response.statusCode());
            delivery.setResponseBody(truncate(response.body(), 1000));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus(WebhookDelivery.Status.SUCCESS);
                delivery.setError(null);
                delivery.setDeliveredAt(LocalDateTime.now());
                delivery.setNextAttemptAt(null);
            } else {
                fail(delivery, "HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            fail(delivery, e.getMessage());
        }

        delivery.setSignature(signature);
        endpoint.setLastDeliveryAt(LocalDateTime.now());
        endpoint.setLastStatus(delivery.getStatus().name());
        endpointRepository.save(endpoint);
    }

    private void fail(WebhookDelivery delivery, String error) {
        delivery.setError(truncate(error, 1000));
        if (delivery.getAttempts() >= delivery.getMaxAttempts()) {
            delivery.setStatus(WebhookDelivery.Status.DEAD);
            delivery.setNextAttemptAt(null);
            log.warn("Webhook delivery {} dead-lettered after {} attempts", delivery.getId(), delivery.getAttempts());
        } else {
            delivery.setStatus(WebhookDelivery.Status.FAILED);
            delivery.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds(delivery.getAttempts())));
        }
    }

    private long backoffSeconds(int attempts) {
        long base = Math.min((long) BASE_BACKOFF_SECONDS * (1L << Math.min(attempts, 10)), MAX_BACKOFF_SECONDS);
        long jitter = (long) (random.nextDouble() * Math.max(base / 4, 1));
        return Math.min(base + jitter, MAX_BACKOFF_SECONDS);
    }

    // ------------------------------------------------------------------ signing

    static String sign(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] result = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign webhook payload", e);
        }
    }

    private String buildPayload(WebhookDelivery delivery) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", delivery.getEventId());
        payload.put("type", delivery.getEventType());
        payload.put("deliveryId", delivery.getId());
        if (delivery.getEventId() != null) {
            eventRepository.findById(delivery.getEventId()).ifPresent(event -> {
                payload.put("workspaceId", event.getWorkspaceId());
                payload.put("resourceType", event.getResourceType());
                payload.put("resourceId", event.getResourceId());
                payload.put("data", readJson(event.getPayload()));
                payload.put("createdAt", event.getCreatedAt());
            });
        }
        return writeJson(payload);
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ helpers

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A webhook URL is required");
        }
        SsrfGuard.assertAllowed(url.trim());
    }

    private String normalizeEventTypes(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return EventService.ALL_EVENTS;
        }
        String joined = eventTypes.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? EventService.ALL_EVENTS : joined;
    }

    private boolean subscribes(WebhookEndpoint endpoint, String eventType) {
        if (endpoint.getEventTypes() == null || endpoint.getEventTypes().isBlank()
                || EventService.ALL_EVENTS.equals(endpoint.getEventTypes())) {
            return true;
        }
        return Arrays.stream(endpoint.getEventTypes().split(","))
                .map(String::trim)
                .anyMatch(t -> t.equals(EventService.ALL_EVENTS) || t.equalsIgnoreCase(eventType));
    }

    private WebhookEndpoint ownedEndpoint(Long id) {
        User user = securityService.getCurrentUser();
        return endpointRepository.findById(id)
                .filter(e -> e.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + id));
    }

    private boolean ownedBy(WebhookDelivery delivery, Long userId) {
        return delivery.getUserId() != null && delivery.getUserId().equals(userId)
                || (delivery.getEndpoint() != null && delivery.getEndpoint().getUser() != null
                        && delivery.getEndpoint().getUser().getId().equals(userId));
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Map<String, Object> toEndpointView(WebhookEndpoint endpoint) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", endpoint.getId());
        view.put("url", endpoint.getUrl());
        view.put("description", endpoint.getDescription());
        view.put("workspaceId", endpoint.getWorkspaceId());
        view.put("eventTypes", endpoint.getEventTypes());
        view.put("active", endpoint.getActive());
        view.put("lastDeliveryAt", endpoint.getLastDeliveryAt());
        view.put("lastStatus", endpoint.getLastStatus());
        view.put("createdAt", endpoint.getCreatedAt());
        return view;
    }

    private Map<String, Object> toDeliveryView(WebhookDelivery delivery) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", delivery.getId());
        view.put("endpointId", delivery.getEndpoint() != null ? delivery.getEndpoint().getId() : null);
        view.put("eventId", delivery.getEventId());
        view.put("eventType", delivery.getEventType());
        view.put("url", delivery.getUrl());
        view.put("status", delivery.getStatus().name());
        view.put("attempts", delivery.getAttempts());
        view.put("maxAttempts", delivery.getMaxAttempts());
        view.put("responseCode", delivery.getResponseCode());
        view.put("error", delivery.getError());
        view.put("nextAttemptAt", delivery.getNextAttemptAt());
        view.put("deliveredAt", delivery.getDeliveredAt());
        view.put("createdAt", delivery.getCreatedAt());
        return view;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}

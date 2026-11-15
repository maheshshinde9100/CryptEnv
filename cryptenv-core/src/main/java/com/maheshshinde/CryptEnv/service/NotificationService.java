package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.DomainEvent;
import com.maheshshinde.CryptEnv.model.Notification;
import com.maheshshinde.CryptEnv.model.NotificationPreference;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.NotificationPreferenceRepository;
import com.maheshshinde.CryptEnv.repository.NotificationRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import com.maheshshinde.CryptEnv.security.SsrfGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Notification channels (§10): in-app notifications plus email (SMTP), Slack and Discord
 * delivery, driven off the {@link DomainEvent} outbox and filtered by per-user preferences.
 * <p>
 * Notification content is derived only from the already-redacted event payload, so no plaintext
 * secret value can ever be notified. Slack / Discord webhook URLs are encrypted at rest with the
 * platform master key and are never returned by the API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    public static final String ALL_EVENTS = "*";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${cryptenv.notifications.enabled:true}")
    boolean notificationsEnabled;

    @Value("${cryptenv.notifications.from:no-reply@cryptenv.local}")
    String mailFrom;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    // ------------------------------------------------------------------ in-app

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(boolean unreadOnly, Pageable pageable) {
        User user = securityService.getCurrentUser();
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(user.getId(), pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        return page.map(this::toView);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> unreadCount() {
        User user = securityService.getCurrentUser();
        return Map.of("unread", notificationRepository.countByUserIdAndReadAtIsNull(user.getId()));
    }

    @Transactional
    public Map<String, Object> markRead(Long id) {
        User user = securityService.getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toView(notification);
    }

    @Transactional
    public Map<String, Object> markAllRead() {
        User user = securityService.getCurrentUser();
        int updated = notificationRepository.markAllRead(user.getId(), LocalDateTime.now());
        return Map.of("updated", updated);
    }

    @Transactional
    public void delete(Long id) {
        User user = securityService.getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        notificationRepository.delete(notification);
    }

    // ------------------------------------------------------------------ preferences

    @Transactional(readOnly = true)
    public Map<String, Object> getPreferences() {
        User user = securityService.getCurrentUser();
        return toPreferenceView(preferenceRepository.findByUserId(user.getId()).orElse(null), user);
    }

    @Transactional
    public Map<String, Object> updatePreferences(Map<String, Object> body) {
        User user = securityService.getCurrentUser();
        NotificationPreference prefs = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> NotificationPreference.builder().userId(user.getId()).build());

        if (body.containsKey("inAppEnabled")) {
            prefs.setInAppEnabled(Boolean.TRUE.equals(body.get("inAppEnabled")));
        }
        if (body.containsKey("emailEnabled")) {
            prefs.setEmailEnabled(Boolean.TRUE.equals(body.get("emailEnabled")));
        }
        if (body.containsKey("emailAddress")) {
            prefs.setEmailAddress(blankToNull(stringValue(body.get("emailAddress"))));
        }
        if (body.containsKey("slackEnabled")) {
            prefs.setSlackEnabled(Boolean.TRUE.equals(body.get("slackEnabled")));
        }
        if (body.containsKey("slackWebhook")) {
            prefs.setSlackWebhookEncrypted(encryptWebhook(stringValue(body.get("slackWebhook"))));
        }
        if (body.containsKey("discordEnabled")) {
            prefs.setDiscordEnabled(Boolean.TRUE.equals(body.get("discordEnabled")));
        }
        if (body.containsKey("discordWebhook")) {
            prefs.setDiscordWebhookEncrypted(encryptWebhook(stringValue(body.get("discordWebhook"))));
        }
        if (body.containsKey("subscribedTypes")) {
            prefs.setSubscribedTypes(normalizeTypes(body.get("subscribedTypes")));
        }
        if (body.containsKey("minSeverity")) {
            prefs.setMinSeverity(parseSeverity(stringValue(body.get("minSeverity"))));
        }

        return toPreferenceView(preferenceRepository.save(prefs), user);
    }

    /** Sends a test message through a channel so the user can verify their configuration. */
    @Transactional(readOnly = true)
    public Map<String, Object> testChannel(String channel) {
        User user = securityService.getCurrentUser();
        NotificationPreference prefs = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> NotificationPreference.builder().userId(user.getId()).build());

        String title = "CryptEnv notification test";
        String body = "This is a test notification for the " + channel + " channel.";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", channel);
        try {
            switch (channel == null ? "" : channel.toLowerCase()) {
                case "email" -> {
                    String to = prefs.getEmailAddress() != null ? prefs.getEmailAddress() : user.getEmail();
                    if (to == null || to.isBlank()) {
                        result.put("delivered", false);
                        result.put("error", "No email address configured");
                        return result;
                    }
                    if (mailSenderProvider.getIfAvailable() == null) {
                        result.put("delivered", false);
                        result.put("error", "SMTP is not configured on this deployment");
                        return result;
                    }
                    sendEmail(to, title, body);
                }
                case "slack" -> {
                    if (prefs.getSlackWebhookEncrypted() == null) {
                        result.put("delivered", false);
                        result.put("error", "No Slack webhook configured");
                        return result;
                    }
                    sendSlack(encryptionService.decryptWithMasterKey(prefs.getSlackWebhookEncrypted()), title, body);
                }
                case "discord" -> {
                    if (prefs.getDiscordWebhookEncrypted() == null) {
                        result.put("delivered", false);
                        result.put("error", "No Discord webhook configured");
                        return result;
                    }
                    sendDiscord(encryptionService.decryptWithMasterKey(prefs.getDiscordWebhookEncrypted()), title, body);
                }
                default -> {
                    result.put("delivered", false);
                    result.put("error", "Unknown channel: " + channel);
                    return result;
                }
            }
            result.put("delivered", true);
        } catch (Exception e) {
            result.put("delivered", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ------------------------------------------------------------------ dispatch

    /**
     * Mirrors a domain event into a notification for the event owner and relays it to the
     * configured external channels. Never throws: notification failure must not block the outbox.
     */
    @Transactional
    public void record(DomainEvent event) {
        if (!notificationsEnabled || event == null || event.getUserId() == null) {
            return;
        }
        NotificationPreference prefs = preferenceRepository.findByUserId(event.getUserId()).orElse(null);
        Notification.Severity severity = severityOf(event.getType());

        if (prefs != null) {
            if (!subscribes(prefs, event.getType())) {
                return;
            }
            if (severity.ordinal() < prefs.getMinSeverity().ordinal()) {
                return;
            }
        }

        String title = titleFor(event);
        String body = bodyFor(event);

        if (prefs == null || Boolean.TRUE.equals(prefs.getInAppEnabled())) {
            notificationRepository.save(Notification.builder()
                    .userId(event.getUserId())
                    .workspaceId(event.getWorkspaceId())
                    .eventId(event.getId())
                    .type(event.getType())
                    .severity(severity)
                    .title(title)
                    .body(body)
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .build());
        }

        if (prefs != null) {
            deliverExternal(prefs, event, title, body);
        }
    }

    private void deliverExternal(NotificationPreference prefs, DomainEvent event, String title, String body) {
        if (Boolean.TRUE.equals(prefs.getSlackEnabled()) && prefs.getSlackWebhookEncrypted() != null) {
            try {
                sendSlack(encryptionService.decryptWithMasterKey(prefs.getSlackWebhookEncrypted()),
                        title, describe(event, body));
            } catch (Exception e) {
                log.warn("Slack notification failed for user {}: {}", prefs.getUserId(), e.getMessage());
            }
        }
        if (Boolean.TRUE.equals(prefs.getDiscordEnabled()) && prefs.getDiscordWebhookEncrypted() != null) {
            try {
                sendDiscord(encryptionService.decryptWithMasterKey(prefs.getDiscordWebhookEncrypted()),
                        title, describe(event, body));
            } catch (Exception e) {
                log.warn("Discord notification failed for user {}: {}", prefs.getUserId(), e.getMessage());
            }
        }
        if (Boolean.TRUE.equals(prefs.getEmailEnabled())) {
            try {
                String to = prefs.getEmailAddress() != null
                        ? prefs.getEmailAddress()
                        : userRepository.findById(prefs.getUserId()).map(User::getEmail).orElse(null);
                if (to != null && !to.isBlank()) {
                    sendEmail(to, "[CryptEnv] " + title, describe(event, body));
                }
            } catch (Exception e) {
                log.warn("Email notification failed for user {}: {}", prefs.getUserId(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ channels

    private void sendSlack(String webhookUrl, String title, String text) throws Exception {
        postJson(webhookUrl, Map.of("text", "*" + title + "*\n" + nullToEmpty(text)));
    }

    private void sendDiscord(String webhookUrl, String title, String text) throws Exception {
        postJson(webhookUrl, Map.of("content", "**" + title + "**\n" + nullToEmpty(text)));
    }

    private void postJson(String webhookUrl, Map<String, Object> payload) throws Exception {
        SsrfGuard.assertAllowed(webhookUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Channel responded with HTTP " + response.statusCode());
        }
    }

    private void sendEmail(String to, String subject, String text) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }

    // ------------------------------------------------------------------ helpers

    private String describe(DomainEvent event, String body) {
        StringBuilder sb = new StringBuilder();
        if (body != null && !body.isBlank()) {
            sb.append(body).append('\n');
        }
        sb.append("Event: ").append(event.getType());
        if (event.getWorkspaceId() != null) {
            sb.append("\nWorkspace: ").append(event.getWorkspaceId());
        }
        return sb.toString();
    }

    /** Derives a severity from the event type. Critical/security-sensitive actions rank highest. */
    static Notification.Severity severityOf(String type) {
        if (type == null) {
            return Notification.Severity.INFO;
        }
        String upper = type.toUpperCase();
        for (String token : new String[]{"LEAK", "BREACH", "EXPOSED", "DEAD", "LOCKED", "DISABLED", "REVOKED"}) {
            if (upper.contains(token)) {
                return Notification.Severity.CRITICAL;
            }
        }
        for (String token : new String[]{"DELETE", "ROTATE", "DRIFT", "FAIL", "DISCONNECT",
                "MFA", "API_KEY", "ROLE", "MEMBER", "REMOVE"}) {
            if (upper.contains(token)) {
                return Notification.Severity.WARNING;
            }
        }
        return Notification.Severity.INFO;
    }

    private Notification.Severity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return Notification.Severity.INFO;
        }
        try {
            return Notification.Severity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown severity: " + value);
        }
    }

    private boolean subscribes(NotificationPreference prefs, String eventType) {
        String types = prefs.getSubscribedTypes();
        if (types == null || types.isBlank() || ALL_EVENTS.equals(types)) {
            return true;
        }
        return Arrays.stream(types.split(","))
                .map(String::trim)
                .anyMatch(t -> t.equals(ALL_EVENTS) || t.equalsIgnoreCase(eventType));
    }

    private String titleFor(DomainEvent event) {
        String words = event.getType() == null ? "Event" : event.getType().replace('_', ' ').toLowerCase();
        return words.isEmpty() ? "Event" : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private String bodyFor(DomainEvent event) {
        Map<String, Object> payload = readJson(event.getPayload());
        StringBuilder sb = new StringBuilder();
        Object details = payload.get("details");
        if (details != null) {
            sb.append(details);
        }
        if (event.getResourceType() != null) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            sb.append(event.getResourceType());
            if (event.getResourceId() != null) {
                sb.append(' ').append(event.getResourceId());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String normalizeTypes(Object value) {
        List<String> types;
        if (value instanceof List<?> list) {
            types = list.stream().map(String::valueOf).toList();
        } else if (value instanceof String s) {
            types = Arrays.stream(s.split(",")).toList();
        } else {
            return ALL_EVENTS;
        }
        String joined = types.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? ALL_EVENTS : joined;
    }

    private String encryptWebhook(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        SsrfGuard.assertAllowed(url.trim());
        return encryptionService.encryptWithMasterKey(url.trim());
    }

    private Map<String, Object> toPreferenceView(NotificationPreference prefs, User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (prefs == null) {
            view.put("inAppEnabled", true);
            view.put("emailEnabled", false);
            view.put("emailAddress", user.getEmail());
            view.put("slackEnabled", false);
            view.put("slackConfigured", false);
            view.put("discordEnabled", false);
            view.put("discordConfigured", false);
            view.put("subscribedTypes", ALL_EVENTS);
            view.put("minSeverity", Notification.Severity.INFO.name());
            view.put("emailConfigured", mailSenderProvider.getIfAvailable() != null);
            return view;
        }
        view.put("inAppEnabled", prefs.getInAppEnabled());
        view.put("emailEnabled", prefs.getEmailEnabled());
        view.put("emailAddress", prefs.getEmailAddress() != null ? prefs.getEmailAddress() : user.getEmail());
        view.put("slackEnabled", prefs.getSlackEnabled());
        view.put("slackConfigured", prefs.getSlackWebhookEncrypted() != null);
        view.put("discordEnabled", prefs.getDiscordEnabled());
        view.put("discordConfigured", prefs.getDiscordWebhookEncrypted() != null);
        view.put("subscribedTypes", prefs.getSubscribedTypes());
        view.put("minSeverity", prefs.getMinSeverity().name());
        view.put("emailConfigured", mailSenderProvider.getIfAvailable() != null);
        view.put("updatedAt", prefs.getUpdatedAt());
        return view;
    }

    private Map<String, Object> toView(Notification notification) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", notification.getId());
        view.put("type", notification.getType());
        view.put("severity", notification.getSeverity().name());
        view.put("title", notification.getTitle());
        view.put("body", notification.getBody());
        view.put("workspaceId", notification.getWorkspaceId());
        view.put("eventId", notification.getEventId());
        view.put("resourceType", notification.getResourceType());
        view.put("resourceId", notification.getResourceId());
        view.put("read", notification.getReadAt() != null);
        view.put("createdAt", notification.getCreatedAt());
        return view;
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

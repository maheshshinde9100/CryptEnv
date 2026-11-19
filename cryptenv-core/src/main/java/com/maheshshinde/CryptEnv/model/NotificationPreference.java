package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Per-user notification preferences (§10): which channels are enabled, the event types the user
 * subscribes to, and the minimum severity that should surface.
 * <p>
 * Slack / Discord webhook URLs embed a bearer token, so they are stored as master-key ciphertext
 * and are never returned by the API.
 */
@Entity
@Table(name = "notification_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private Boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private Boolean emailEnabled = false;

    @Column(name = "email_address", length = 255)
    private String emailAddress;

    @Column(name = "slack_enabled", nullable = false)
    @Builder.Default
    private Boolean slackEnabled = false;

    @Column(name = "slack_webhook_encrypted", columnDefinition = "TEXT")
    private String slackWebhookEncrypted;

    @Column(name = "discord_enabled", nullable = false)
    @Builder.Default
    private Boolean discordEnabled = false;

    @Column(name = "discord_webhook_encrypted", columnDefinition = "TEXT")
    private String discordWebhookEncrypted;

    /** Comma-separated event types the user subscribes to, or {@code *} for all. */
    @Column(name = "subscribed_types", nullable = false, length = 1000)
    @Builder.Default
    private String subscribedTypes = "*";

    @Enumerated(EnumType.STRING)
    @Column(name = "min_severity", nullable = false, length = 20)
    @Builder.Default
    private Notification.Severity minSeverity = Notification.Severity.INFO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * An outbound webhook endpoint (§10). Requests are signed with HMAC-SHA256 using a
 * per-endpoint secret. The signing secret is stored as master-key ciphertext and is
 * never returned by the API.
 */
@Entity
@Table(name = "webhook_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 255)
    private String description;

    @Column(name = "secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String secretEncrypted;

    /** Comma-separated event types, or {@code *} for all events. */
    @Column(name = "event_types", nullable = false, length = 1000)
    @Builder.Default
    private String eventTypes = "*";

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_delivery_at")
    private LocalDateTime lastDeliveryAt;

    @Column(name = "last_status", length = 20)
    private String lastStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

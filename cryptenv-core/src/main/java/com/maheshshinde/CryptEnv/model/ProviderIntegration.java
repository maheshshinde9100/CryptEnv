package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A connected external secret provider (GitHub, Vercel, Cloudflare, Kubernetes, ...).
 * Credentials are stored as master-key encrypted ciphertext; only {@code hasCredentials}
 * is exposed to clients.
 */
@Entity
@Table(name = "provider_integrations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_provider_integration_user_provider", columnNames = {"user_id", "provider"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderIntegration {

    public enum Status { CONNECTED, ERROR, DISCONNECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(length = 150)
    private String name;

    @Column(name = "encrypted_credentials", nullable = false, columnDefinition = "TEXT")
    private String encryptedCredentials;

    @Column(columnDefinition = "TEXT")
    private String config;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.CONNECTED;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Records the fingerprint of the value last successfully pushed to a provider target.
 * Used by drift detection to spot divergence without ever storing plaintext.
 */
@Entity
@Table(name = "provider_sync_state", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sync_state_integration_target_key",
                columnNames = {"integration_id", "target", "secret_key"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id", nullable = false)
    private ProviderIntegration integration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "environment_id")
    private Long environmentId;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 255)
    private String target;

    @Column(name = "secret_key", nullable = false, length = 255)
    private String secretKey;

    @Column(name = "last_fingerprint", length = 64)
    private String lastFingerprint;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_status", nullable = false, length = 20)
    @Builder.Default
    private String lastStatus = "SUCCESS";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

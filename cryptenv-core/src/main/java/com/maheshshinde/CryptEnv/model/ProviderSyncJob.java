package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A queued provider-sync unit of work. Rows are picked up by {@code ProviderSyncWorker}
 * and retried with exponential backoff + jitter. After {@code maxAttempts} the job is
 * moved to the {@code DEAD} dead-letter state and can be replayed.
 */
@Entity
@Table(name = "provider_sync_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSyncJob {

    public enum Status { PENDING, RUNNING, SUCCESS, FAILED, DEAD }

    public enum Operation { PUSH, DELETE }

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Operation operation = Operation.PUSH;

    @Column(name = "payload_encrypted", columnDefinition = "TEXT")
    private String payloadEncrypted;

    @Column(name = "secret_key", length = 255)
    private String secretKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(columnDefinition = "TEXT")
    private String results;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One per-secret drift classification produced by a {@link DriftScan}.
 * {@code IN_SYNC} = matches the pushed fingerprint, {@code MODIFIED} = value changed since
 * push, {@code MISSING} = present on one side only, {@code STALE} = synced longer ago than
 * the configured threshold.
 */
@Entity
@Table(name = "drift_findings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriftFinding {

    public enum Classification { IN_SYNC, STALE, MODIFIED, MISSING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private DriftScan scan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "environment_id")
    private Long environmentId;

    @Column(name = "secret_key", nullable = false, length = 255)
    private String secretKey;

    @Column(length = 40)
    private String provider;

    @Column(length = 255)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Classification classification;

    /** Fingerprint of the value last pushed to the provider (or null when never pushed). */
    @Column(name = "expected_fingerprint", length = 64)
    private String expectedFingerprint;

    /** Fingerprint of the secret's current value in CryptEnv. */
    @Column(name = "actual_fingerprint", length = 64)
    private String actualFingerprint;

    @Column(length = 1000)
    private String detail;

    @Column(name = "detected_at", nullable = false)
    @Builder.Default
    private LocalDateTime detectedAt = LocalDateTime.now();
}

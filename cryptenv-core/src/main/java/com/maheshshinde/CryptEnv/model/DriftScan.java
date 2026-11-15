package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A single drift-detection run over one environment. Stores only summary counts; the
 * per-secret detail lives in {@link DriftFinding}.
 */
@Entity
@Table(name = "drift_scans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriftScan {

    public enum Status { RUNNING, COMPLETED, FAILED }

    public enum Trigger { MANUAL, SCHEDULED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "environment_id")
    private Long environmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Trigger trigger = Trigger.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.RUNNING;

    @Column(nullable = false)
    @Builder.Default
    private Integer total = 0;

    @Column(name = "in_sync", nullable = false)
    @Builder.Default
    private Integer inSync = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer stale = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer modified = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer missing = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(length = 1000)
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

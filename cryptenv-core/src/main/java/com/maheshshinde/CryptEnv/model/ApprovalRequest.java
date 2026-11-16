package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A four-eyes approval request (§10). A destructive action is queued as a {@code PENDING}
 * request and only executed once a <em>different</em> eligible user approves it.
 */
@Entity
@Table(name = "approval_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    public enum Status { PENDING, APPROVED, REJECTED, EXECUTED, FAILED, CANCELLED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "resource_type", length = 40)
    private String resourceType;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    /** JSON parameters for the deferred action. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "result_detail", length = 1000)
    private String resultDetail;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

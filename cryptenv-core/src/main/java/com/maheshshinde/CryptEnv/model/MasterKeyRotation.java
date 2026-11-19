package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "master_key_rotations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterKeyRotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "old_key_hash", nullable = false)
    private String oldKeyHash;

    @Column(name = "new_key_hash", nullable = false)
    private String newKeyHash;

    @Column(name = "started_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(nullable = false)
    @Builder.Default
    private String status = "in_progress";

    @Column(name = "total_secrets", nullable = false)
    @Builder.Default
    private Integer totalSecrets = 0;

    @Column(name = "processed_secrets", nullable = false)
    @Builder.Default
    private Integer processedSecrets = 0;

    @Column(name = "failed_secrets", nullable = false)
    @Builder.Default
    private Integer failedSecrets = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}

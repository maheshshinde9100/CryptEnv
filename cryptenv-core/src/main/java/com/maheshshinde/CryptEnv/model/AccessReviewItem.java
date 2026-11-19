package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_review_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_review_id", nullable = false)
    private Long accessReviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "workspace_member_id")
    private Long workspaceMemberId;

    @Column
    private String role;

    @Column(nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

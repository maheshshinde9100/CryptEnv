package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "review_period_start", nullable = false)
    private LocalDateTime reviewPeriodStart;

    @Column(name = "review_period_end", nullable = false)
    private LocalDateTime reviewPeriodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

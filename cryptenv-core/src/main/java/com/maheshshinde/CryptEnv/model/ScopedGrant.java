package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "scoped_grants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScopedGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_member_id", nullable = false)
    private Long workspaceMemberId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "environment_id")
    private Long environmentId;

    @Column(name = "grant_type", nullable = false)
    private String grantType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String permissions;

    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}

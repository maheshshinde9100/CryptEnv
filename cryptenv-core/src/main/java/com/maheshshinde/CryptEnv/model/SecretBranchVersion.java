package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "secret_branch_versions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"secret_branch_id", "secret_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretBranchVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "secret_branch_id", nullable = false)
    private Long secretBranchId;

    @Column(name = "secret_id", nullable = false)
    private Long secretId;

    @Column(name = "secret_version_id", nullable = false)
    private Long secretVersionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

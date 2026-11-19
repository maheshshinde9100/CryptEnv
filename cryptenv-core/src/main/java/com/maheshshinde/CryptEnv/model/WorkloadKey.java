package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workload_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkloadKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_identity_id", nullable = false)
    private Long machineIdentityId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false)
    private String publicKey;

    @Column(name = "wrapped_private_key", nullable = false)
    private String wrappedPrivateKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}

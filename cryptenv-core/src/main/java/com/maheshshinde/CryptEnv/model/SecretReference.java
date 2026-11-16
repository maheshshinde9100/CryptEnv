package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "secret_references")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "secret_id", nullable = false)
    private Long secretId;

    @Column(name = "reference_key", nullable = false)
    private String referenceKey;

    @Column(name = "target_secret_id")
    private Long targetSecretId;

    @Column(name = "target_environment_id")
    private Long targetEnvironmentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

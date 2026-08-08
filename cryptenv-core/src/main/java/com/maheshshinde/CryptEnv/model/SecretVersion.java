package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "secret_versions", indexes = {
    @Index(name = "idx_secret_version_key", columnList = "secret_key"),
    @Index(name = "idx_secret_version_number", columnList = "secret_key,version_number"),
    @Index(name = "idx_secret_version_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "secret_key", nullable = false, length = 255)
    private String secretKey;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
    private String encryptedValue;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "rotation_reason", length = 255)
    private String rotationReason;

    @Column(name = "rotated_by_email", length = 100)
    private String rotatedByEmail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}

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
@Table(name = "secrets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"environment_id", "key"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Secret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean encrypted = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean versioned = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;
    
    @Column(name = "current_version")
    @Builder.Default
    private Integer currentVersion = 1;
    
    @Column(name = "auto_rotate")
    @Builder.Default
    private Boolean autoRotate = false;
    
    @Column(name = "rotation_interval_days")
    private Integer rotationIntervalDays;
    
    @Column(name = "last_rotated_at")
    private LocalDateTime lastRotatedAt;
    
    @Column(name = "next_rotation_at")
    private LocalDateTime nextRotationAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "updated_by_email")
    private String updatedByEmail;
    
    @Column(name = "encrypted_value", columnDefinition = "TEXT")
    private String encryptedValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

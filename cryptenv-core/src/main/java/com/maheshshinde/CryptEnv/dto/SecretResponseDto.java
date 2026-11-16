package com.maheshshinde.CryptEnv.dto;

import com.maheshshinde.CryptEnv.model.Secret;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretResponseDto {

    private Long id;
    private String key;
    private String value;
    private Long environmentId;
    private String description;
    private Boolean encrypted;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Secret.SecretType secretType;
    private String tags;
    private Boolean isActive;
    private Boolean isDeleted;
    private Boolean autoRotate;
    private Integer rotationIntervalDays;
    private LocalDateTime lastRotatedAt;
    private LocalDateTime nextRotationAt;
    private LocalDateTime expiresAt;
}

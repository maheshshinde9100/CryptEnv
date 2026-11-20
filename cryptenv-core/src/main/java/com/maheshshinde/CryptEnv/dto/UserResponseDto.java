package com.maheshshinde.CryptEnv.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    private Long id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private String role;
    private Boolean mfaEnabled;
    private Boolean emailVerified;
    /** Full key — only present immediately after create/regenerate; never stored. */
    private String apiKey;
    /** Visible prefix for listing (e.g. ce_live_abcd1234). */
    private String apiKeyPrefix;
    private LocalDateTime apiKeyCreatedAt;
    private LocalDateTime apiKeyExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

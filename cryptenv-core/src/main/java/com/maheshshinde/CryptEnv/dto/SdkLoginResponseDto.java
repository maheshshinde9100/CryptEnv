package com.maheshshinde.CryptEnv.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SdkLoginResponseDto {

    private String token;
    private Long userId;
    private String email;
    private String username;
    private List<SdkWorkspaceDto> workspaces;
    private LocalDateTime issuedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SdkWorkspaceDto {
        private Long id;
        private String name;
        private String description;
        private Boolean hasEncryptionKey;
        private List<SdkEnvironmentDto> environments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SdkEnvironmentDto {
        private Long id;
        private String name;
        private Boolean isActive;
    }
}

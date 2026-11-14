package com.maheshshinde.CryptEnv.dto;

import com.maheshshinde.CryptEnv.model.Environment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentResponseDto {

    private Long id;
    private Environment.EnvironmentType name;
    private String label;
    private String displayName;
    private Long workspaceId;
    private String workspaceName;
    private Long projectId;
    private String projectName;
    private Boolean isActive;
    private Boolean isProtected;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

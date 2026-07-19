package com.maheshshinde.CryptEnv.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceResponseDto {

    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private String ownerUsername;
    private Set<String> memberUsernames;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

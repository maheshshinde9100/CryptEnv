package com.maheshshinde.CryptEnv.dto;

import com.maheshshinde.CryptEnv.model.Environment;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentCreateDto {

    @NotNull(message = "Environment type is required")
    private Environment.EnvironmentType name;

    @NotNull(message = "Workspace ID is required")
    private Long workspaceId;
}

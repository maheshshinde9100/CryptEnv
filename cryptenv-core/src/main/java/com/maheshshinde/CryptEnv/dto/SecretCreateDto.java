package com.maheshshinde.CryptEnv.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretCreateDto {

    @NotBlank(message = "Key is required")
    @Size(max = 255, message = "Key must not exceed 255 characters")
    private String key;

    @NotBlank(message = "Value is required")
    private String value;

    private Long environmentId;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Builder.Default
    private Boolean encrypted = true;
}

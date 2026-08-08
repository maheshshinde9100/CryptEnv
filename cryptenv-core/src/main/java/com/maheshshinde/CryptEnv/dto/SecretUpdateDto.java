package com.maheshshinde.CryptEnv.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretUpdateDto {

    @NotBlank(message = "Value is required")
    private String value;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}

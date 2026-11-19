package com.maheshshinde.CryptEnv.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaDisableDto {

    @NotBlank(message = "Password is required")
    private String password;

    /** TOTP code or recovery code. */
    @NotBlank(message = "MFA code is required")
    private String code;
}

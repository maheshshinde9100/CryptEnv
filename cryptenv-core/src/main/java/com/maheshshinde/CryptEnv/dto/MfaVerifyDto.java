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
public class MfaVerifyDto {

    @NotBlank(message = "MFA challenge token is required")
    private String mfaToken;

    /** TOTP code or recovery code. */
    @NotBlank(message = "Code is required")
    private String code;
}

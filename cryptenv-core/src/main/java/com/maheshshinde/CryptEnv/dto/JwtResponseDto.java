package com.maheshshinde.CryptEnv.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponseDto {

    private String token;
    private String refreshToken;
    /** Access token lifetime in seconds. */
    private Long expiresIn;
    private String type = "Bearer";
    private Long userId;
    private String username;
    private String email;

    /** When true, client must call /api/auth/mfa/verify with mfaToken + TOTP/recovery code. */
    private Boolean mfaRequired;
    /** Short-lived challenge token issued when MFA is required after password login. */
    private String mfaToken;
}

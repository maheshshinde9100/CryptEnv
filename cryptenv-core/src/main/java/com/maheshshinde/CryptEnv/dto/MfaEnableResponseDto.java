package com.maheshshinde.CryptEnv.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaEnableResponseDto {

    private boolean enabled;
    /** Shown once — store securely; codes are hashed at rest. */
    private List<String> recoveryCodes;
}

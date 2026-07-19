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
    private String type = "Bearer";
    private Long userId;
    private String username;
    private String email;
}

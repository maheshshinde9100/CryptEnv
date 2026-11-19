package com.maheshshinde.CryptEnv.dto;

import com.maheshshinde.CryptEnv.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberDto {
    private Long userId;
    private String username;
    private String email;
    private Role role;
}

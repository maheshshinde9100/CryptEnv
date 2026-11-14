package com.maheshshinde.CryptEnv.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberId implements Serializable {
    private Long workspace;
    private Long user;
}

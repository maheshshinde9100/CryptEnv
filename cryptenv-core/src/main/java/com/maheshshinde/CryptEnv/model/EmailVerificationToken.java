package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Hashed, short-lived registration email verification code. One active code exists per user. */
@Entity
@Table(name = "email_verification_tokens", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmailVerificationToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;
}

package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}

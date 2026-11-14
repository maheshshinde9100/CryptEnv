package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.AccountLockedException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Progressive account lockout after repeated failed logins.
 * Locks after {@link #MAX_FAILURES_BEFORE_LOCK} failures; delay doubles each subsequent lock.
 */
@Service
@RequiredArgsConstructor
public class AccountLockoutService {

    public static final int MAX_FAILURES_BEFORE_LOCK = 5;
    /** First lock duration after the 5th failure (seconds). */
    public static final long BASE_LOCK_SECONDS = 60L;
    /** Cap progressive delay at 24 hours. */
    public static final long MAX_LOCK_SECONDS = 24L * 3600L;

    private final UserRepository userRepository;

    public boolean isLocked(User user) {
        if (user == null) {
            return false;
        }
        LocalDateTime until = user.getLockedUntil();
        return until != null && until.isAfter(LocalDateTime.now());
    }

    public void assertNotLocked(User user) {
        if (user == null) {
            return;
        }
        LocalDateTime until = user.getLockedUntil();
        if (until != null && until.isAfter(LocalDateTime.now())) {
            throw new AccountLockedException(
                    "Account is locked due to too many failed login attempts. Try again later.",
                    until
            );
        }
    }

    /**
     * Seconds to lock for a given failure count (0 if below threshold).
     * failures=5 → 60s, 6 → 120s, 7 → 240s, … capped at 24h.
     */
    public static long lockDurationSeconds(int failedAttempts) {
        if (failedAttempts < MAX_FAILURES_BEFORE_LOCK) {
            return 0L;
        }
        int level = failedAttempts - MAX_FAILURES_BEFORE_LOCK; // 0,1,2,...
        int shift = Math.min(level, 16);
        long seconds = BASE_LOCK_SECONDS << shift;
        return Math.min(seconds, MAX_LOCK_SECONDS);
    }

    /** Separate transaction so the counter survives auth failure rollbacks. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(Long userId) {
        if (userId == null) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(attempts);

        long lockSecs = lockDurationSeconds(attempts);
        if (lockSecs > 0) {
            LocalDateTime until = LocalDateTime.now().plusSeconds(lockSecs);
            user.setLockedUntil(until);
            user.setAccountNonLocked(false);
        }
        userRepository.save(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearLockout(Long userId) {
        if (userId == null) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setAccountNonLocked(true);
        userRepository.save(user);
    }
}

package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.AccountLockedException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AccountLockoutService")
@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountLockoutService accountLockoutService;

    @Test
    void lockDurationEscalatesAfterFiveFailures() {
        assertEquals(0L, AccountLockoutService.lockDurationSeconds(0));
        assertEquals(0L, AccountLockoutService.lockDurationSeconds(4));
        assertEquals(60L, AccountLockoutService.lockDurationSeconds(5));
        assertEquals(120L, AccountLockoutService.lockDurationSeconds(6));
        assertEquals(240L, AccountLockoutService.lockDurationSeconds(7));
        assertEquals(480L, AccountLockoutService.lockDurationSeconds(8));
        assertEquals(AccountLockoutService.MAX_LOCK_SECONDS,
                AccountLockoutService.lockDurationSeconds(5 + 20));
    }

    @Test
    void isLockedWhenLockedUntilInFuture() {
        User user = User.builder()
                .lockedUntil(LocalDateTime.now().plusMinutes(5))
                .build();
        assertTrue(accountLockoutService.isLocked(user));
        assertThrows(AccountLockedException.class, () -> accountLockoutService.assertNotLocked(user));
    }

    @Test
    void isNotLockedWhenLockExpired() {
        User user = User.builder()
                .lockedUntil(LocalDateTime.now().minusMinutes(1))
                .accountNonLocked(false)
                .build();
        assertFalse(accountLockoutService.isLocked(user));
        assertDoesNotThrow(() -> accountLockoutService.assertNotLocked(user));
    }

    @Test
    void recordFailedLoginLocksOnFifthAttempt() {
        User user = User.builder()
                .id(1L)
                .failedLoginAttempts(4)
                .accountNonLocked(true)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountLockoutService.recordFailedLogin(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(5, saved.getFailedLoginAttempts());
        assertFalse(saved.getAccountNonLocked());
        assertNotNull(saved.getLockedUntil());
        assertTrue(saved.getLockedUntil().isAfter(LocalDateTime.now().plusSeconds(50)));
    }

    @Test
    void recordFailedLoginDoesNotLockBeforeThreshold() {
        User user = User.builder()
                .id(2L)
                .failedLoginAttempts(2)
                .accountNonLocked(true)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountLockoutService.recordFailedLogin(2L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(3, saved.getFailedLoginAttempts());
        assertTrue(saved.getAccountNonLocked());
        assertNull(saved.getLockedUntil());
    }

    @Test
    void clearLockoutResetsCounters() {
        User user = User.builder()
                .id(3L)
                .failedLoginAttempts(7)
                .lockedUntil(LocalDateTime.now().plusHours(1))
                .accountNonLocked(false)
                .build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountLockoutService.clearLockout(3L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(0, saved.getFailedLoginAttempts());
        assertNull(saved.getLockedUntil());
        assertTrue(saved.getAccountNonLocked());
    }
}

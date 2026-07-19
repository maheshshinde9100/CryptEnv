package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.UserRegistrationDto;
import com.maheshshinde.CryptEnv.dto.UserResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserRegistrationDto registrationDto;
    private User user;

    @BeforeEach
    void setUp() {
        registrationDto = UserRegistrationDto.builder()
                .email("test@example.com")
                .username("testuser")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .build();

        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .password("encodedPassword")
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void registerUser_Success() {
        when(userRepository.existsByEmail(registrationDto.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(registrationDto.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(registrationDto.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponseDto result = userService.registerUser(registrationDto);

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertEquals("testuser", result.getUsername());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_EmailAlreadyExists() {
        when(userRepository.existsByEmail(registrationDto.getEmail())).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> userService.registerUser(registrationDto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_UsernameAlreadyExists() {
        when(userRepository.existsByEmail(registrationDto.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(registrationDto.getUsername())).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> userService.registerUser(registrationDto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponseDto result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void getUserById_NotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(1L));
    }

    @Test
    void getUserByEmail_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserResponseDto result = userService.getUserByEmail("test@example.com");

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        verify(userRepository, times(1)).findByEmail("test@example.com");
    }

    @Test
    void getUserByEmail_NotFound() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserByEmail("test@example.com"));
    }
}

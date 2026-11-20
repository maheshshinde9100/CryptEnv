package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.UserRegistrationDto;
import com.maheshshinde.CryptEnv.dto.UserResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.security.ApiKeyHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public UserResponseDto registerUser(UserRegistrationDto registrationDto) {
        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new ResourceAlreadyExistsException("Email already registered: " + registrationDto.getEmail());
        }

        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            throw new ResourceAlreadyExistsException("Username already taken: " + registrationDto.getUsername());
        }

        String rawApiKey = ApiKeyHasher.generateRawKey();

        User user = User.builder()
                .email(registrationDto.getEmail())
                .username(registrationDto.getUsername())
                .password(passwordEncoder.encode(registrationDto.getPassword()))
                .firstName(registrationDto.getFirstName())
                .lastName(registrationDto.getLastName())
                .apiKey(null)
                .apiKeyHash(ApiKeyHasher.sha256Hex(rawApiKey))
                .apiKeyPrefix(ApiKeyHasher.prefixOf(rawApiKey))
                .apiKeyCreatedAt(LocalDateTime.now())
                .role(com.maheshshinde.CryptEnv.model.Role.OWNER)
                .enabled(false)
                .emailVerified(false)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        User savedUser = userRepository.save(user);
        emailVerificationService.issue(savedUser);
        return mapToResponseDto(savedUser, rawApiKey);
    }

    @Transactional
    public UserResponseDto generateNewApiKey(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        String rawApiKey = ApiKeyHasher.generateRawKey();
        user.setApiKey(null);
        user.setApiKeyHash(ApiKeyHasher.sha256Hex(rawApiKey));
        user.setApiKeyPrefix(ApiKeyHasher.prefixOf(rawApiKey));
        user.setApiKeyCreatedAt(LocalDateTime.now());
        user.setApiKeyExpiresAt(null);
        User updated = userRepository.save(user);
        return mapToResponseDto(updated, rawApiKey);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapToResponseDto(user, null);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapToResponseDto(user, null);
    }

    private UserResponseDto mapToResponseDto(User user, String rawApiKeyOnce) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(user.getEnabled())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .mfaEnabled(user.getMfaEnabled())
                .emailVerified(user.getEmailVerified())
                .apiKey(rawApiKeyOnce)
                .apiKeyPrefix(user.getApiKeyPrefix())
                .apiKeyCreatedAt(user.getApiKeyCreatedAt())
                .apiKeyExpiresAt(user.getApiKeyExpiresAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

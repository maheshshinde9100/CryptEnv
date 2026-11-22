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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailAddressService emailAddressService;

    @Transactional
    public UserResponseDto registerUser(UserRegistrationDto registrationDto) {
        String email = emailAddressService.requireDeliverable(registrationDto.getEmail());
        registrationDto.setEmail(email);
        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException("Email already registered: " + email);
        }

        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            throw new ResourceAlreadyExistsException("Username already taken: " + registrationDto.getUsername());
        }

        String rawApiKey = ApiKeyHasher.generateRawKey();

        User user = User.builder()
                .email(email)
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

    /**
     * Creates a verified account for an OAuth identity. The account gets a random
     * password (password login stays closed until the user resets it) and an API key.
     */
    @Transactional
    public User findOrCreateOAuthUser(String rawEmail, String displayName) {
        String email = EmailAddressService.normalize(rawEmail);
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("OAuth provider did not return an email address");
        }
        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            boolean changed = false;
            if (!Boolean.TRUE.equals(existing.getEmailVerified())) {
                existing.setEmailVerified(true);
                changed = true;
            }
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(true);
                changed = true;
            }
            return changed ? userRepository.save(existing) : existing;
        }

        String base = email.substring(0, email.indexOf('@'))
                .replaceAll("[^A-Za-z0-9._-]", "")
                .toLowerCase();
        if (base.length() < 3) base = "user";
        if (base.length() > 40) base = base.substring(0, 40);
        String username = base;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + suffix++;
        }

        String firstName = null;
        String lastName = null;
        if (displayName != null && !displayName.isBlank()) {
            String[] parts = displayName.trim().split("\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : null;
        }

        String rawApiKey = ApiKeyHasher.generateRawKey();
        User user = User.builder()
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .firstName(firstName)
                .lastName(lastName)
                .apiKeyHash(ApiKeyHasher.sha256Hex(rawApiKey))
                .apiKeyPrefix(ApiKeyHasher.prefixOf(rawApiKey))
                .apiKeyCreatedAt(LocalDateTime.now())
                .role(com.maheshshinde.CryptEnv.model.Role.OWNER)
                .enabled(true)
                .emailVerified(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        return userRepository.save(user);
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

package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.dto.UserLoginDto;
import com.maheshshinde.CryptEnv.dto.UserRegistrationDto;
import com.maheshshinde.CryptEnv.dto.UserResponseDto;
import com.maheshshinde.CryptEnv.exception.InvalidCredentialsException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.security.JwtTokenProvider;
import com.maheshshinde.CryptEnv.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final TokenService tokenService;

    public UserService getUserService() {
        return userService;
    }

    @Transactional
    public UserResponseDto register(UserRegistrationDto registrationDto) {
        return userService.registerUser(registrationDto);
    }

    @Transactional
    public JwtResponseDto authenticate(UserLoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDto.getEmail(),
                        loginDto.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        TokenService.TokenPair pair = tokenService.issueTokenPair(user);

        return JwtResponseDto.builder()
                .token(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(tokenProvider.getAccessTokenTtlMs() / 1000)
                .userId(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .email(userPrincipal.getEmail())
                .build();
    }

    @Transactional
    public JwtResponseDto refresh(String refreshToken) {
        TokenService.TokenPair pair = tokenService.rotateRefreshToken(refreshToken);
        Long userId = tokenProvider.getUserIdFromToken(pair.accessToken());
        User user = tokenService.requireUser(userId);
        return JwtResponseDto.builder()
                .token(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(tokenProvider.getAccessTokenTtlMs() / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    @Transactional
    public void logout(String accessToken, boolean everywhere) {
        if (accessToken != null && !accessToken.isBlank()) {
            tokenService.revokeAccessToken(accessToken);
            if (everywhere && tokenProvider.validateToken(accessToken)) {
                tokenService.revokeAllSessions(tokenProvider.getUserIdFromToken(accessToken));
            }
        }
    }

    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new InvalidCredentialsException("User not authenticated");
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return userService.getUserById(userPrincipal.getId());
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}

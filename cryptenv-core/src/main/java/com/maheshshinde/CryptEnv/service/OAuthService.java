package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.UserResponseDto;
import com.maheshshinde.CryptEnv.model.OAuthConnection;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.OAuthConnectionRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final OAuthConnectionRepository oauthConnectionRepository;
    private final UserRepository userRepository;

    @Transactional
    public OAuthConnection linkOAuth(Long userId, String provider, String providerUserId, 
                                     String accessToken, String refreshToken, LocalDateTime tokenExpiresAt,
                                     String email, String name, String avatarUrl) {
        OAuthConnection connection = OAuthConnection.builder()
                .userId(userId)
                .provider(provider)
                .providerUserId(providerUserId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenExpiresAt(tokenExpiresAt)
                .email(email)
                .name(name)
                .avatarUrl(avatarUrl)
                .build();
        return oauthConnectionRepository.save(connection);
    }

    @Transactional
    public OAuthConnection updateTokens(String provider, String providerUserId, 
                                       String accessToken, String refreshToken, LocalDateTime tokenExpiresAt) {
        Optional<OAuthConnection> connOpt = oauthConnectionRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (connOpt.isEmpty()) {
            throw new IllegalArgumentException("OAuth connection not found");
        }
        OAuthConnection connection = connOpt.get();
        connection.setAccessToken(accessToken);
        connection.setRefreshToken(refreshToken);
        connection.setTokenExpiresAt(tokenExpiresAt);
        return oauthConnectionRepository.save(connection);
    }

    public Optional<User> findUserByOAuth(String provider, String providerUserId) {
        return oauthConnectionRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .flatMap(conn -> userRepository.findById(conn.getUserId()));
    }

    public List<OAuthConnection> getUserConnections(Long userId) {
        return oauthConnectionRepository.findByUserId(userId);
    }

    @Transactional
    public void unlinkOAuth(Long userId, String provider) {
        oauthConnectionRepository.findByUserId(userId).stream()
                .filter(conn -> conn.getProvider().equals(provider))
                .findFirst()
                .ifPresent(oauthConnectionRepository::delete);
    }
}

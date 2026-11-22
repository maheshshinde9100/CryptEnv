package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.OAuthConnection;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.OAuthConnectionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/** Google OAuth2 authorization-code login with signed state and HTTPS token exchange. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    private static final String AUTHORIZE_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final long STATE_TTL_SECONDS = 600;

    /** Paths that actually complete a Google login; anything else would 400 after consent. */
    private static final Set<String> CALLBACK_PATHS = Set.of(
            "/api/auth/oauth/google/callback",
            "/login/oauth2/code/google");

    private final UserService userService;
    private final OAuthService oauthService;
    private final OAuthConnectionRepository connectionRepository;
    private final TokenService tokenService;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String clientSecret;

    @Value("${GOOGLE_REDIRECT_URI:}")
    private String configuredRedirectUri;

    @Value("${CRYPTENV_DASHBOARD_URL:http://localhost:3000}")
    private String dashboardUrl;

    @Value("${CRYPTENV_OAUTH_ALLOWED_ORIGINS:}")
    private String additionalOrigins;

    @Value("${JWT_SECRET}")
    private String stateSecret;

    private RestClient http;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(10));
        http = RestClient.builder().requestFactory(factory).build();
        log.info("GoogleOAuth: client={}, redirect={}, dashboard={}, extraOrigins={}",
                StringUtils.hasText(clientId)
                        ? clientId.substring(0, Math.min(20, clientId.length())) + "…" : "NOT SET",
                StringUtils.hasText(configuredRedirectUri)
                        ? configuredRedirectUri.trim() : "derived per request",
                dashboardUrl,
                StringUtils.hasText(additionalOrigins) ? additionalOrigins : "none");
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    public String redirectUri(String derived) {
        String configured = configuredRedirectUri == null ? "" : configuredRedirectUri.trim();
        if (configured.isEmpty()) return derived;
        if (CALLBACK_PATHS.stream().noneMatch(configured::contains)) {
            log.warn("GOOGLE_REDIRECT_URI is set to {} which does not serve the CryptEnv callback; " +
                    "using the derived URI {} instead. Register {} in Google Console.",
                    configured, derived, derived);
            return derived;
        }
        return configured;
    }

    public String authorizationUrl(String requestedOrigin, String redirectUri) {
        String origin = safeOrigin(requestedOrigin);
        String state = signState(origin);
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "select_account")
                .build()
                .encode()
                .toUriString();
    }

    public record LoginResult(String redirectUrl, User user) {}

    /** Exchanges the code, verifies the identity, issues CryptEnv tokens, and returns a dashboard redirect. */
    public LoginResult callback(String code, String state, String error, String redirectUri) {
        String origin = dashboardUrl;
        try {
            origin = verifyState(state);
            if (StringUtils.hasText(error)) {
                return new LoginResult(errorRedirect(origin, "Google sign-in was cancelled"), null);
            }
            if (!StringUtils.hasText(code)) {
                return new LoginResult(errorRedirect(origin, "Google did not return an authorization code"), null);
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", code);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri);
            form.add("grant_type", "authorization_code");

            @SuppressWarnings("rawtypes")
            Map tokens = http.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (tokens == null || tokens.get("access_token") == null) {
                return new LoginResult(errorRedirect(origin, "Google token exchange failed"), null);
            }
            String accessToken = String.valueOf(tokens.get("access_token"));
            String refreshToken = tokens.get("refresh_token") == null ? null : String.valueOf(tokens.get("refresh_token"));
            LocalDateTime expiresAt = tokens.get("expires_in") == null ? null
                    : LocalDateTime.now().plusSeconds(((Number) tokens.get("expires_in")).longValue());

            @SuppressWarnings("rawtypes")
            Map profile = http.get()
                    .uri(USERINFO_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            if (profile == null || profile.get("email") == null) {
                return new LoginResult(errorRedirect(origin, "Google did not return an email address"), null);
            }
            Object verified = profile.get("email_verified");
            if (verified != null && !Boolean.parseBoolean(String.valueOf(verified))) {
                return new LoginResult(errorRedirect(origin, "That Google email is not verified"), null);
            }

            String providerUserId = String.valueOf(profile.get("sub"));
            String email = String.valueOf(profile.get("email"));
            String name = profile.get("name") == null ? null : String.valueOf(profile.get("name"));
            String picture = profile.get("picture") == null ? null : String.valueOf(profile.get("picture"));

            User user = userService.findOrCreateOAuthUser(email, name);

            OAuthConnection connection = connectionRepository
                    .findByProviderAndProviderUserId("google", providerUserId)
                    .orElse(null);
            if (connection != null) {
                oauthService.updateTokens("google", providerUserId, accessToken, refreshToken, expiresAt);
            } else {
                oauthService.linkOAuth(user.getId(), "google", providerUserId,
                        accessToken, refreshToken, expiresAt, email, name, picture);
            }

            TokenService.TokenPair pair = tokenService.issueTokenPair(user);
            String redirect = UriComponentsBuilder.fromUriString(origin + "/auth/google/callback")
                    .fragment("token=" + pair.accessToken() + "&refreshToken=" + pair.refreshToken())
                    .build()
                    .toUriString();
            return new LoginResult(redirect, user);
        } catch (Exception e) {
            log.warn("Google OAuth callback failed: {}", e.getMessage());
            return new LoginResult(errorRedirect(origin, "Google sign-in failed. Please try again."), null);
        }
    }

    private String errorRedirect(String origin, String message) {
        return origin + "/auth/google/callback#error="
                + java.net.URLEncoder.encode(message, StandardCharsets.UTF_8);
    }

    /** Only allow redirects back to the configured dashboard (or localhost during development). */
    private String safeOrigin(String requested) {
        String fallback = originOf(dashboardUrl);
        if (!StringUtils.hasText(requested)) return fallback;
        try {
            URI uri = URI.create(requested.trim());
            if (uri.getScheme() == null || uri.getHost() == null) return fallback;
            if (!uri.getScheme().equals("https") && !uri.getScheme().equals("http")) return fallback;
            String candidate = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            if (candidate.equals(fallback)) return candidate;
            String host = uri.getHost();
            if (host.equals("localhost") || host.equals("127.0.0.1")) return candidate;
            if (StringUtils.hasText(additionalOrigins)) {
                boolean allowed = Arrays.stream(additionalOrigins.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .anyMatch(o -> originOf(o).equals(candidate));
                if (allowed) return candidate;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return fallback;
    }

    private String originOf(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }

    private String signState(String origin) {
        String payload = origin + "|" + Instant.now().getEpochSecond();
        return base64(payload.getBytes(StandardCharsets.UTF_8)) + "." + base64(hmac(payload));
    }

    private String verifyState(String state) {
        if (!StringUtils.hasText(state) || !state.contains(".")) {
            throw new IllegalArgumentException("Missing OAuth state");
        }
        String[] parts = state.split("\\.", 2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        byte[] expected = hmac(payload);
        byte[] actual = Base64.getUrlDecoder().decode(parts[1]);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("Invalid OAuth state signature");
        }
        int separator = payload.lastIndexOf('|');
        long issuedAt = Long.parseLong(payload.substring(separator + 1));
        if (Instant.now().getEpochSecond() - issuedAt > STATE_TTL_SECONDS) {
            throw new IllegalArgumentException("OAuth state expired");
        }
        String origin = payload.substring(0, separator);
        if (!origin.equals(safeOrigin(origin))) {
            throw new IllegalArgumentException("OAuth state origin is not allowed");
        }
        return origin;
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC is unavailable", e);
        }
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
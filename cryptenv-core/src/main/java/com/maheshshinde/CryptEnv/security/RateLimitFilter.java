package com.maheshshinde.CryptEnv.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter to reduce abuse / accidental DDoS load on production.
 * Uses Bucket4j in-memory buckets (sufficient for single-instance Render deploys).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${cryptenv.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${cryptenv.rate-limit.capacity:100}")
    private long capacity;

    @Value("${cryptenv.rate-limit.refill-per-minute:100}")
    private long refillPerMinute;

    @Value("${cryptenv.rate-limit.auth-capacity:20}")
    private long authCapacity;

    @Value("${cryptenv.rate-limit.auth-refill-per-minute:20}")
    private long authRefillPerMinute;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        return path != null && (path.equals("/api/health") || path.equals("/health"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = clientIp(request);
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        boolean authPath = path.startsWith("/api/auth/") || path.equals("/api/sdk/login");
        String bucketKey = (authPath ? "auth:" : "api:") + ip;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> authPath ? authBucket() : apiBucket());

        if (bucket.tryConsume(1)) {
            long remaining = bucket.getAvailableTokens();
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit exceeded for ip={} path={}", ip, path);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Slow down and retry shortly.\"}"
        );
    }

    private Bucket apiBucket() {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket authBucket() {
        Bandwidth limit = Bandwidth.classic(authCapacity, Refill.greedy(authRefillPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}

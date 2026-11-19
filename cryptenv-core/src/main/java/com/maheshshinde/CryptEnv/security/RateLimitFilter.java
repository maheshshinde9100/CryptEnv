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
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-IP rate limiter. Uses Bucket4j in-memory buckets (single-instance).
 * Only trusts X-Forwarded-For / X-Real-IP when cryptenv.rate-limit.trust-proxy=true.
 * Evicts idle bucket entries to avoid unbounded growth.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long EVICT_EVERY_N_REQUESTS = 256;
    private static final Duration BUCKET_IDLE_TTL = Duration.ofMinutes(30);

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

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

    /** When false (default), ignore spoofable forwarded headers and use remoteAddr only. */
    @Value("${cryptenv.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

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
        if (requestCounter.incrementAndGet() % EVICT_EVERY_N_REQUESTS == 0) {
            evictIdleBuckets();
        }

        String ip = clientIp(request);
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        boolean authPath = path.startsWith("/api/auth/") || path.equals("/api/sdk/login");
        String bucketKey = (authPath ? "auth:" : "api:") + ip;

        BucketEntry entry = buckets.compute(bucketKey, (k, existing) -> {
            if (existing == null) {
                return new BucketEntry(authPath ? authBucket() : apiBucket());
            }
            existing.touch();
            return existing;
        });

        if (entry.bucket.tryConsume(1)) {
            long remaining = entry.bucket.getAvailableTokens();
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

    private void evictIdleBuckets() {
        Instant cutoff = Instant.now().minus(BUCKET_IDLE_TTL);
        Iterator<Map.Entry<String, BucketEntry>> it = buckets.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            Map.Entry<String, BucketEntry> e = it.next();
            if (e.getValue().lastAccess.isBefore(cutoff)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Evicted {} idle rate-limit buckets; remaining={}", removed, buckets.size());
        }
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
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static final class BucketEntry {
        final Bucket bucket;
        volatile Instant lastAccess;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = Instant.now();
        }

        void touch() {
            this.lastAccess = Instant.now();
        }
    }
}

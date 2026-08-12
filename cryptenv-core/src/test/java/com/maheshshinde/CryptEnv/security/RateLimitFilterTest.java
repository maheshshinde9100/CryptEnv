package com.maheshshinde.CryptEnv.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimit — Bucket4j token bucket sanity")
class RateLimitFilterTest {

    @Test
    @DisplayName("bucket allows capacity tokens then rejects")
    void bucketEnforcesCapacity() {
        Bandwidth limit = Bandwidth.classic(3, Refill.greedy(3, Duration.ofMinutes(1)));
        Bucket bucket = Bucket.builder().addLimit(limit).build();
        assertTrue(bucket.tryConsume(1));
        assertTrue(bucket.tryConsume(1));
        assertTrue(bucket.tryConsume(1));
        assertFalse(bucket.tryConsume(1));
    }
}

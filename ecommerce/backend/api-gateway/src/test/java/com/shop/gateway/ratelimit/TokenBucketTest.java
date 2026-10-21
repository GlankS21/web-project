package com.shop.gateway.ratelimit;

import com.shop.gateway.config.RateLimitPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    private final AtomicLong clock = new AtomicLong();
    private final TokenBucket bucket = new TokenBucket(
            new RateLimitPolicy(3, 1, Duration.ofSeconds(1)), clock::get);

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
        assertThat(bucket.availableTokens()).isZero();
    }

    @Test
    void refillsOverTime() {
        for (int i = 0; i < 3; i++) {
            bucket.tryConsume();
        }

        clock.addAndGet(Duration.ofMillis(1500).toNanos());

        assertThat(bucket.availableTokens()).isEqualTo(1);
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void neverExceedsCapacity() {
        clock.addAndGet(Duration.ofHours(1).toNanos());

        assertThat(bucket.availableTokens()).isEqualTo(3);
    }
}

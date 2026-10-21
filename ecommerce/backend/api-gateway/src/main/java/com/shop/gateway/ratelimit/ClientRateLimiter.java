package com.shop.gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shop.gateway.config.RateLimitPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Keeps one {@link TokenBucket} per (policy, client) pair in a bounded, self-expiring cache.
 * <p>
 * State is local to this gateway instance. When the gateway is scaled horizontally the effective
 * limit is multiplied by the number of instances; a shared store (e.g. Redis) would be required
 * for a strict global limit.
 */
@Component
public class ClientRateLimiter {

    private static final long MAX_TRACKED_CLIENTS = 100_000;
    private static final Duration IDLE_EXPIRY = Duration.ofMinutes(10);

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_CLIENTS)
            .expireAfterAccess(IDLE_EXPIRY)
            .build();

    public RateLimitDecision tryAcquire(String policyName, RateLimitPolicy policy, String clientKey) {
        TokenBucket bucket = buckets.get(policyName + ':' + clientKey,
                key -> new TokenBucket(policy, System::nanoTime));
        boolean allowed = bucket.tryConsume();
        return new RateLimitDecision(allowed, bucket.availableTokens());
    }
}

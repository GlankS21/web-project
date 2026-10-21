package com.shop.gateway.config;

import java.time.Duration;

/**
 * Token bucket parameters: a client may burst up to {@code capacity} requests, then
 * {@code refillTokens} requests become available every {@code refillPeriod}.
 */
public record RateLimitPolicy(long capacity, long refillTokens, Duration refillPeriod) {

    public RateLimitPolicy {
        if (capacity <= 0 || refillTokens <= 0) {
            throw new IllegalArgumentException("Rate limit capacity and refill tokens must be positive");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("Rate limit refill period must be positive");
        }
    }
}

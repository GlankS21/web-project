package com.shop.gateway.ratelimit;

import com.shop.gateway.config.RateLimitPolicy;

import java.util.function.LongSupplier;

/**
 * Thread-safe token bucket with continuous refill.
 */
public class TokenBucket {

    private final long capacity;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;

    private double availableTokens;
    private long lastRefillNanos;

    public TokenBucket(RateLimitPolicy policy, LongSupplier nanoClock) {
        this.capacity = policy.capacity();
        this.tokensPerNano = (double) policy.refillTokens() / policy.refillPeriod().toNanos();
        this.nanoClock = nanoClock;
        this.availableTokens = policy.capacity();
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (availableTokens >= 1) {
            availableTokens -= 1;
            return true;
        }
        return false;
    }

    public synchronized long availableTokens() {
        refill();
        return (long) availableTokens;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        availableTokens = Math.min(capacity, availableTokens + elapsed * tokensPerNano);
        lastRefillNanos = now;
    }
}

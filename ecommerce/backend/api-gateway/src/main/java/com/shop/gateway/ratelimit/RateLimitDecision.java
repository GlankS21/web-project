package com.shop.gateway.ratelimit;

/**
 * Outcome of a rate limit check.
 *
 * @param allowed   whether the request may proceed
 * @param remaining tokens left for the client after this request
 */
public record RateLimitDecision(boolean allowed, long remaining) {
}

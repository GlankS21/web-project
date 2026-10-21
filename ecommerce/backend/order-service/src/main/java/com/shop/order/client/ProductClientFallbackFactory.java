package com.shop.order.client;

import com.shop.common.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Called when product-service fails, times out or its circuit breaker is open. Prices must never be
 * guessed, so checkout is refused with 503 rather than degraded.
 */
@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return ids -> {
            log.warn("product-service call failed: {}", cause.toString());
            throw new ServiceUnavailableException("The product catalogue is temporarily unavailable, please retry", cause);
        };
    }
}

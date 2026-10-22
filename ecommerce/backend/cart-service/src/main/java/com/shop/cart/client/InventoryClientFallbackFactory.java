package com.shop.cart.client;

import com.shop.common.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Called when inventory-service fails, times out or its circuit breaker is open. The cart treats
 * the stock as unknown: the order saga makes the final stock check anyway.
 */
@Slf4j
@Component
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    static final String STOCK_UNAVAILABLE = "cart.error.stock-unavailable";

    @Override
    public InventoryClient create(Throwable cause) {
        return productIds -> {
            log.warn("inventory-service call failed: {}", cause.toString());
            throw new ServiceUnavailableException(STOCK_UNAVAILABLE, cause);
        };
    }
}

package com.shop.cart.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param maxItems           distinct products per cart
 * @param maxQuantityPerItem units per cart line
 */
@Validated
@ConfigurationProperties(prefix = "app.cart")
public record CartProperties(
        @Min(1) Integer maxItems,
        @Min(1) Integer maxQuantityPerItem
) {

    public CartProperties {
        maxItems = maxItems == null ? 50 : maxItems;
        maxQuantityPerItem = maxQuantityPerItem == null ? 99 : maxQuantityPerItem;
    }
}

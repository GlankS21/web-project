package com.shop.product.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param currency ISO 4217 code of every catalogue price (single-currency store)
 */
@Validated
@ConfigurationProperties(prefix = "app.catalog")
public record CatalogProperties(
        @Pattern(regexp = "^[A-Z]{3}$") String currency
) {

    public CatalogProperties {
        currency = currency == null ? "VND" : currency;
    }
}

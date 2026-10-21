package com.shop.order.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param maxItems           distinct products per order
 * @param maxQuantityPerItem units per order line
 * @param shipping           shipping fee rules
 * @param paymentTimeout     orders still waiting for stock or payment after this delay are cancelled
 * @param outbox             relay of saga events to RabbitMQ
 */
@Validated
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(
        @Min(1) Integer maxItems,
        @Min(1) Integer maxQuantityPerItem,
        @Valid Shipping shipping,
        Duration paymentTimeout,
        @Valid Outbox outbox
) {

    public OrderProperties {
        maxItems = maxItems == null ? 50 : maxItems;
        maxQuantityPerItem = maxQuantityPerItem == null ? 99 : maxQuantityPerItem;
        shipping = shipping == null ? new Shipping(null, null) : shipping;
        paymentTimeout = paymentTimeout == null ? Duration.ofMinutes(15) : paymentTimeout;
        outbox = outbox == null ? new Outbox(null, null, null) : outbox;
    }

    /**
     * @param flatFee       fee charged below the threshold
     * @param freeThreshold subtotal from which shipping is free
     */
    public record Shipping(BigDecimal flatFee, BigDecimal freeThreshold) {

        public Shipping {
            flatFee = flatFee == null ? BigDecimal.ZERO : flatFee;
            freeThreshold = freeThreshold == null ? BigDecimal.ZERO : freeThreshold;
        }

        public BigDecimal feeFor(BigDecimal subtotal) {
            return subtotal.compareTo(freeThreshold) >= 0 ? BigDecimal.ZERO : flatFee;
        }
    }

    /**
     * @param pollInterval delay between two relay runs
     * @param batchSize    events published per run
     * @param retention    how long published events are kept before deletion
     */
    public record Outbox(Duration pollInterval, @Min(1) Integer batchSize, Duration retention) {

        public Outbox {
            pollInterval = pollInterval == null ? Duration.ofSeconds(1) : pollInterval;
            batchSize = batchSize == null ? 50 : batchSize;
            retention = retention == null ? Duration.ofDays(7) : retention;
        }
    }
}

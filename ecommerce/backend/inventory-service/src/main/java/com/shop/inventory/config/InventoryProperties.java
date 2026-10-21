package com.shop.inventory.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param defaultLowStockThreshold threshold given to stock records created automatically
 * @param outbox                   relay of saga replies to RabbitMQ
 */
@Validated
@ConfigurationProperties(prefix = "app.inventory")
public record InventoryProperties(
        @Min(0) Integer defaultLowStockThreshold,
        @Valid Outbox outbox
) {

    public InventoryProperties {
        defaultLowStockThreshold = defaultLowStockThreshold == null ? 5 : defaultLowStockThreshold;
        outbox = outbox == null ? new Outbox(null, null, null) : outbox;
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

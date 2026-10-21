package com.shop.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param simulator behaviour of the simulated payment gateway
 * @param outbox    relay of saga replies to RabbitMQ
 */
@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        @Valid Simulator simulator,
        @Valid Outbox outbox
) {

    public PaymentProperties {
        simulator = simulator == null ? new Simulator(null, null) : simulator;
        outbox = outbox == null ? new Outbox(null, null, null) : outbox;
    }

    /**
     * @param maxAmount   card and e-wallet payments above this amount are declined
     * @param failureRate share of the other card and e-wallet payments declined at random
     */
    public record Simulator(BigDecimal maxAmount, @DecimalMin("0.0") @DecimalMax("1.0") Double failureRate) {

        public Simulator {
            maxAmount = maxAmount == null ? new BigDecimal("50000000") : maxAmount;
            failureRate = failureRate == null ? 0.0 : failureRate;
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

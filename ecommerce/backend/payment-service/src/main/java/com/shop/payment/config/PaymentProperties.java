package com.shop.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param provider       {@code yookassa} or {@code mock} (local imitation of the payment widget)
 * @param currency       the only currency accepted for online payments (ISO 4217)
 * @param maxAttempts    charge attempts allowed per order (first payment and "pay again")
 * @param yookassa       YooKassa API access
 * @param reconciliation polling of operations whose outcome the provider has not notified yet
 * @param outbox         relay of saga replies to RabbitMQ
 */
@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        Provider provider,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Min(1) Integer maxAttempts,
        @Valid YooKassa yookassa,
        @Valid Reconciliation reconciliation,
        @Valid Outbox outbox
) {

    public PaymentProperties {
        provider = provider == null ? Provider.MOCK : provider;
        currency = currency == null ? "RUB" : currency;
        maxAttempts = maxAttempts == null ? 5 : maxAttempts;
        yookassa = yookassa == null ? new YooKassa(null, null, null, null, null) : yookassa;
        reconciliation = reconciliation == null ? new Reconciliation(null, null, null, null) : reconciliation;
        outbox = outbox == null ? new Outbox(null, null, null) : outbox;
    }

    public enum Provider {
        MOCK,
        YOOKASSA
    }

    /**
     * @param apiUrl         base address of API v3
     * @param shopId         shop identifier (from the YooKassa dashboard)
     * @param secretKey      secret key of the shop; a test shop has a {@code test_...} key
     * @param connectTimeout TCP connection timeout
     * @param readTimeout    response timeout
     */
    public record YooKassa(String apiUrl, String shopId, String secretKey, Duration connectTimeout,
                           Duration readTimeout) {

        public YooKassa {
            apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.yookassa.ru/v3" : stripTrailingSlash(apiUrl);
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(20) : readTimeout;
        }

        @Override
        public String toString() {
            // Never print the secret key, e.g. in a configuration dump
            return "YooKassa[apiUrl=%s, shopId=%s]".formatted(apiUrl, shopId);
        }
    }

    /**
     * @param interval  delay between two runs
     * @param minAge    operations younger than this are left to the webhook and the return page
     * @param maxAge    pending operations older than this are given up as failed
     * @param batchSize operations checked per run
     */
    public record Reconciliation(Duration interval, Duration minAge, Duration maxAge, @Min(1) Integer batchSize) {

        public Reconciliation {
            interval = interval == null ? Duration.ofMinutes(1) : interval;
            minAge = minAge == null ? Duration.ofMinutes(1) : minAge;
            maxAge = maxAge == null ? Duration.ofHours(24) : maxAge;
            batchSize = batchSize == null ? 50 : batchSize;
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

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

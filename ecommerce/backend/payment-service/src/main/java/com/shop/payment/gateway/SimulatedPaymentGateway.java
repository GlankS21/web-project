package com.shop.payment.gateway;

import com.shop.payment.config.PaymentProperties;
import com.shop.payment.entity.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Stand-in for a payment provider, deterministic enough to demonstrate both saga outcomes:
 * <ul>
 *     <li>cash on delivery is always accepted;</li>
 *     <li>card and e-wallet payments above {@code app.payment.simulator.max-amount} are declined;</li>
 *     <li>other card and e-wallet payments are declined with probability {@code failure-rate}.</li>
 * </ul>
 */
@Slf4j
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final PaymentProperties.Simulator settings;
    private final RandomGenerator random;

    @Autowired
    public SimulatedPaymentGateway(PaymentProperties properties) {
        // java.util.Random is in java.base (RandomGenerator.getDefault() needs the jdk.random module)
        this(properties, new Random());
    }

    SimulatedPaymentGateway(PaymentProperties properties, RandomGenerator random) {
        this.settings = properties.simulator();
        this.random = random;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (request.method() == PaymentMethod.COD) {
            return ChargeResult.approved(reference("COD"));
        }
        if (request.amount().compareTo(settings.maxAmount()) > 0) {
            return ChargeResult.declined("Amount %s %s exceeds the limit of %s %s".formatted(
                    request.amount().toPlainString(), request.currency(),
                    settings.maxAmount().toPlainString(), request.currency()));
        }
        if (settings.failureRate() > 0 && random.nextDouble() < settings.failureRate()) {
            return ChargeResult.declined("Declined by the issuer");
        }
        return ChargeResult.approved(reference("TXN"));
    }

    @Override
    public String refund(String transactionId, BigDecimal amount, String currency) {
        String refundId = reference("RFD");
        log.info("Simulated refund {} of {} {} for transaction {}", refundId, amount, currency, transactionId);
        return refundId;
    }

    private static String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }
}

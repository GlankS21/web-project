package com.shop.payment.gateway;

import com.shop.payment.config.PaymentProperties;
import com.shop.payment.entity.PaymentMethod;
import com.shop.payment.gateway.PaymentGateway.ChargeRequest;
import com.shop.payment.gateway.PaymentGateway.ChargeResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

    private final PaymentProperties properties =
            new PaymentProperties(new PaymentProperties.Simulator(new BigDecimal("1000000"), 0.5), null);

    @Test
    void acceptsCashOnDeliveryWhateverTheAmount() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(properties, alwaysDraw(0.0));

        ChargeResult result = gateway.charge(request("99000000", PaymentMethod.COD));

        assertThat(result.approved()).isTrue();
        assertThat(result.transactionId()).matches("COD-[0-9A-F]{16}");
    }

    @Test
    void declinesAmountsAboveTheLimit() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(properties, alwaysDraw(0.99));

        ChargeResult result = gateway.charge(request("1000001", PaymentMethod.CARD));

        assertThat(result.approved()).isFalse();
        assertThat(result.declineReason()).isEqualTo("Amount 1000001 VND exceeds the limit of 1000000 VND");
    }

    @Test
    void declinesAtRandomWithTheConfiguredRate() {
        assertThat(new SimulatedPaymentGateway(properties, alwaysDraw(0.49))
                .charge(request("1000", PaymentMethod.E_WALLET)).declineReason()).isEqualTo("Declined by the issuer");

        ChargeResult approved = new SimulatedPaymentGateway(properties, alwaysDraw(0.5))
                .charge(request("1000", PaymentMethod.E_WALLET));
        assertThat(approved.approved()).isTrue();
        assertThat(approved.transactionId()).matches("TXN-[0-9A-F]{16}");
    }

    @Test
    void refundsWithAReference() {
        assertThat(new SimulatedPaymentGateway(properties).refund("TXN-1", BigDecimal.TEN, "VND"))
                .matches("RFD-[0-9A-F]{16}");
    }

    private static ChargeRequest request(String amount, PaymentMethod method) {
        return new ChargeRequest(UUID.randomUUID(), "ORD-1", new BigDecimal(amount), "VND", method);
    }

    private static RandomGenerator alwaysDraw(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}

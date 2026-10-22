package com.shop.payment.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Same names as the payment methods of order-service, received as text in {@code order.payment-requested}.
 */
public enum PaymentMethod {

    /** Cash on delivery: accepted immediately, collected by the courier. */
    COD,

    /** Paid on the page of the payment provider (bank card, SBP, YooMoney...), chosen there by the customer. */
    ONLINE;

    public static Optional<PaymentMethod> parse(String value) {
        return Arrays.stream(values()).filter(method -> method.name().equals(value)).findFirst();
    }
}

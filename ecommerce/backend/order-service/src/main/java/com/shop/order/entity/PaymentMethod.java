package com.shop.order.entity;

/**
 * Payment methods offered at checkout. The name is sent to payment-service in
 * {@code order.payment-requested}.
 */
public enum PaymentMethod {

    /** Cash on delivery: accepted immediately, collected by the courier. */
    COD,

    CARD,

    E_WALLET
}

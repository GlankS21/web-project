package com.shop.order.entity;

/**
 * Payment methods offered at checkout. The name is sent to payment-service in
 * {@code order.payment-requested}.
 */
public enum PaymentMethod {

    /** Cash on delivery: accepted immediately, collected by the courier. */
    COD,

    /** Paid on the page of the payment provider (bank card, SBP, YooMoney...), chosen there by the customer. */
    ONLINE
}

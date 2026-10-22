package com.shop.inventory.entity;

public enum MovementType {

    /** Manual change of the on-hand quantity by an administrator. */
    ADJUSTMENT,

    /** Units held for an order (reserved quantity increases). */
    RESERVATION,

    /** A reservation was given back after the order was cancelled. */
    RELEASE,

    /** Reserved units left the warehouse after the order was paid. */
    SALE,

    /** Sold units came back to the stock after a paid order was cancelled before shipping. */
    RETURN
}

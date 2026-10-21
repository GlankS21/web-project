package com.shop.cart.dto.response;

/**
 * Whether a cart line can be ordered as it is.
 */
public enum CartItemStatus {

    AVAILABLE,

    /** Some units are in stock, fewer than the quantity of the line. */
    INSUFFICIENT_STOCK,

    OUT_OF_STOCK,

    /** The product is no longer sold (unpublished or archived). */
    UNAVAILABLE,

    /** The catalogue or the stock could not be checked right now. */
    UNKNOWN;

    public boolean blocksCheckout() {
        return this == INSUFFICIENT_STOCK || this == OUT_OF_STOCK || this == UNAVAILABLE;
    }
}

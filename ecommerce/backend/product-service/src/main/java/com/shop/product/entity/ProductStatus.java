package com.shop.product.entity;

/**
 * Only {@link #ACTIVE} products are visible to customers and can be added to a cart.
 */
public enum ProductStatus {

    /** Being prepared by an administrator. */
    DRAFT,

    /** Published in the catalogue. */
    ACTIVE,

    /** No longer sold; kept because past orders reference it. */
    ARCHIVED
}

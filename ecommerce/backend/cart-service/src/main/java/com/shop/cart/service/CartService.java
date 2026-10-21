package com.shop.cart.service;

import com.shop.cart.dto.request.AddCartItemRequest;
import com.shop.cart.dto.response.CartResponse;
import com.shop.common.event.OrderCreatedEvent;

import java.util.UUID;

/**
 * The cart of the authenticated customer. Every response is priced with the current catalogue
 * prices and checked against the available stock.
 */
public interface CartService {

    CartResponse getCart(UUID userId);

    /**
     * Adds units of a product, creating the cart or the line when needed.
     */
    CartResponse addItem(UUID userId, AddCartItemRequest request);

    CartResponse updateItem(UUID userId, Long productId, int quantity);

    CartResponse removeItem(UUID userId, Long productId);

    void clearCart(UUID userId);

    /**
     * Handler of {@code order.created}: the ordered products leave the cart. Removing whole lines
     * keeps the handler idempotent under redelivery.
     */
    void removeOrderedProducts(OrderCreatedEvent event);
}

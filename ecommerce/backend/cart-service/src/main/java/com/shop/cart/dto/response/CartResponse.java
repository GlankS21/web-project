package com.shop.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param totalQuantity  units in the cart
 * @param subtotal       sum of the lines that are still sold (shipping is computed at checkout)
 * @param checkoutReady  no line is unavailable or short of stock
 * @param pricesUpToDate {@code false} when the catalogue could not be reached and saved prices are shown
 */
public record CartResponse(
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal subtotal,
        String currency,
        boolean checkoutReady,
        boolean pricesUpToDate
) {

    public static CartResponse empty() {
        return new CartResponse(List.of(), 0, BigDecimal.ZERO, null, false, true);
    }
}

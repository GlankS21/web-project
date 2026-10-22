package com.shop.order.service;

import com.shop.common.dto.PageResponse;
import com.shop.common.security.AuthenticatedUser;
import com.shop.order.dto.request.CouponPreviewRequest;
import com.shop.order.dto.request.CreateOrderRequest;
import com.shop.order.dto.request.UpdateOrderStatusRequest;
import com.shop.order.dto.response.CouponPreviewResponse;
import com.shop.order.dto.response.OrderResponse;
import com.shop.order.dto.response.OrderSummaryResponse;
import com.shop.order.entity.OrderStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Checkout and order management. The outcome of a new order is decided asynchronously by the saga.
 */
public interface OrderService {

    /**
     * Prices the items from product-service, saves the order as PENDING and starts the saga.
     *
     * An optional coupon is redeemed in the same transaction; an invalid coupon fails the checkout.
     *
     * @param idempotencyKey optional; a retry with the same key returns the order created the first time
     */
    OrderPlacement placeOrder(AuthenticatedUser customer, CreateOrderRequest request, String idempotencyKey);

    /**
     * Prices the items as {@link #placeOrder} does and applies the coupon, without reserving anything.
     *
     * @throws com.shop.common.exception.BusinessException when the coupon cannot be used for this cart
     */
    CouponPreviewResponse previewCoupon(AuthenticatedUser customer, CouponPreviewRequest request);

    PageResponse<OrderSummaryResponse> listOrdersOf(UUID userId, OrderStatus status, Pageable pageable);

    /**
     * Customers only see their own orders (others are reported as not found); administrators see all.
     */
    OrderResponse getOrder(UUID orderId, AuthenticatedUser caller);

    /**
     * Allowed until the order is paid, by its owner or an administrator.
     */
    OrderResponse cancelOrder(UUID orderId, AuthenticatedUser caller, String reason);

    PageResponse<OrderSummaryResponse> searchOrders(OrderStatus status, UUID userId, String keyword, Pageable pageable);

    /**
     * Delivery flow driven by an administrator: PAID → SHIPPED → DELIVERED. Delivery publishes
     * {@code order.delivered}.
     */
    OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request, UUID adminId);

    /**
     * @param created {@code false} when an earlier order with the same idempotency key was returned
     */
    record OrderPlacement(OrderResponse order, boolean created) {
    }
}

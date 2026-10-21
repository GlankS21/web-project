package com.shop.order.controller;

import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.security.SecurityUtils;
import com.shop.order.dto.request.CancelOrderRequest;
import com.shop.order.dto.request.CreateOrderRequest;
import com.shop.order.dto.response.OrderResponse;
import com.shop.order.dto.response.OrderSummaryResponse;
import com.shop.order.entity.OrderStatus;
import com.shop.order.service.OrderService;
import com.shop.order.service.OrderService.OrderPlacement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Orders", description = "Checkout and order history of the authenticated customer")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final OrderService orderService;

    @Operation(summary = "Place an order",
            description = "Prices come from the catalogue. The order is returned as PENDING; stock reservation "
                    + "and payment follow asynchronously, poll GET /api/orders/{id} until PAID or CANCELLED. "
                    + "Send an Idempotency-Key header to make retries safe (201 first time, 200 on replay).")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> place(
            @Parameter(description = "8 to 100 letters, digits, '-' or '_', unique per checkout attempt")
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderPlacement placement = orderService.placeOrder(SecurityUtils.currentUser(), request, idempotencyKey);
        return ResponseEntity.status(placement.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(placement.created() ? "Order placed" : "Order already placed",
                        placement.order()));
    }

    @Operation(summary = "My orders, newest first")
    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> myOrders(
            @RequestParam(name = "status", required = false) OrderStatus status,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(orderService.listOrdersOf(SecurityUtils.currentUserId(), status, pageable));
    }

    @Operation(summary = "Order details with items and status timeline",
            description = "Customers can only read their own orders; administrators can read any order.")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> get(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(orderService.getOrder(orderId, SecurityUtils.currentUser()));
    }

    @Operation(summary = "Cancel an order that is not paid yet",
            description = "Reserved stock is released. Allowed for the owner and for administrators.")
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable("orderId") UUID orderId,
                                             @Valid @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.success("Order cancelled", orderService.cancelOrder(orderId, SecurityUtils.currentUser(), reason));
    }
}

package com.shop.payment.controller;

import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.security.SecurityUtils;
import com.shop.payment.dto.response.PaymentResponse;
import com.shop.payment.entity.PaymentStatus;
import com.shop.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Payments", description = "Payments are created by the order saga; this API only reads them")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Payment of an order",
            description = "Customers can only read the payment of their own orders; administrators can read any.")
    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentResponse> ofOrder(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(paymentService.getPaymentOfOrder(orderId, SecurityUtils.currentUser()));
    }

    @Operation(summary = "All payments, newest first (admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> search(
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(paymentService.searchPayments(status, pageable));
    }
}

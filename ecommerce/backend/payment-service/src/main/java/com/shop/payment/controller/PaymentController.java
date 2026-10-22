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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Платежи", description = "Платежи создаются сагой заказа; покупатель подтверждает онлайн-оплату "
        + "на странице платёжного сервиса и после отказа может оплатить заказ повторно")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Платёж по заказу",
            description = "С токеном виджета текущей попытки (confirmationToken), пока платёж в статусе PENDING, "
                    + "и историей попыток и возвратов. Покупатель видит только платежи своих заказов, "
                    + "администратор — любые.")
    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentResponse> ofOrder(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(paymentService.getPaymentOfOrder(orderId, SecurityUtils.currentUser()));
    }

    @Operation(summary = "Оплатить повторно",
            description = "Начинает новую попытку после отклонённой или прерванной онлайн-оплаты и возвращает её "
                    + "confirmationToken, например чтобы оплатить другой картой. Пока попытка ещё не завершена, "
                    + "она возвращается без изменений. Только владелец заказа и только пока заказ ожидает оплаты.")
    @PostMapping("/orders/{orderId}/retry")
    public ApiResponse<PaymentResponse> retry(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(paymentService.retryPayment(orderId, SecurityUtils.currentUser()));
    }

    @Operation(summary = "Обновить данные у платёжного сервиса",
            description = "Запрашивает у платёжного сервиса состояние незавершённых попыток и возвратов, например "
                    + "когда виджет сообщил об окончании оплаты раньше, чем пришло уведомление.")
    @PostMapping("/orders/{orderId}/refresh")
    public ApiResponse<PaymentResponse> refresh(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(paymentService.refreshPayment(orderId, SecurityUtils.currentUser()));
    }

    @Operation(summary = "Все платежи, сначала новые (только для администратора)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> search(
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(paymentService.searchPayments(status, pageable));
    }
}

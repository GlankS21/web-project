package com.shop.order.controller;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.security.SecurityUtils;
import com.shop.order.dto.request.UpdateOrderStatusRequest;
import com.shop.order.dto.response.OrderResponse;
import com.shop.order.dto.response.OrderSummaryResponse;
import com.shop.order.entity.OrderStatus;
import com.shop.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Success messages are resolved from {@code messages_ru.properties}.
 */
@Tag(name = "Управление заказами", description = "Все заказы и доставка (только для администраторов)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/orders/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final MessageSource messageSource;

    @Operation(summary = "Поиск заказов")
    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> search(
            @Parameter(description = "Фильтр по статусу заказа")
            @RequestParam(name = "status", required = false) OrderStatus status,
            @Parameter(description = "Идентификатор покупателя")
            @RequestParam(name = "userId", required = false) UUID userId,
            @Parameter(description = "Часть номера заказа или e-mail покупателя")
            @RequestParam(name = "keyword", required = false) String keyword,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(orderService.searchOrders(status, userId, keyword, pageable));
    }

    @Operation(summary = "Перевести оплаченный заказ в статус SHIPPED, а отправленный — в DELIVERED")
    @PatchMapping("/{orderId}/status")
    public ApiResponse<OrderResponse> updateStatus(@PathVariable("orderId") UUID orderId,
                                                   @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.success(message("order.status-updated"),
                orderService.updateStatus(orderId, request, SecurityUtils.currentUserId()));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}

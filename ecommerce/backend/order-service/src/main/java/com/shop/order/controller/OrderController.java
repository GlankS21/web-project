package com.shop.order.controller;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.security.SecurityUtils;
import com.shop.order.dto.request.CancelOrderRequest;
import com.shop.order.dto.request.CouponPreviewRequest;
import com.shop.order.dto.request.CreateOrderRequest;
import com.shop.order.dto.response.CouponPreviewResponse;
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
import org.springframework.context.MessageSource;
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

/**
 * Success messages are resolved from {@code messages_ru.properties}.
 */
@Tag(name = "Заказы", description = "Оформление заказа и история заказов текущего покупателя")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final OrderService orderService;
    private final MessageSource messageSource;

    @Operation(summary = "Оформить заказ",
            description = "Цены берутся из каталога. Заказ возвращается в статусе PENDING; резервирование товара "
                    + "и оплата выполняются асинхронно — запрашивайте GET /api/orders/{id}, пока статус не станет "
                    + "PAID или CANCELLED. Передайте заголовок Idempotency-Key, чтобы повторные запросы были "
                    + "безопасны (201 при первом запросе, 200 при повторе).")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> place(
            @Parameter(description = "От 8 до 100 латинских букв, цифр, «-» или «_», уникален для каждой попытки оформления")
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderPlacement placement = orderService.placeOrder(SecurityUtils.currentUser(), request, idempotencyKey);
        return ResponseEntity.status(placement.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(message(placement.created() ? "order.placed" : "order.already-placed"),
                        placement.order()));
    }

    @Operation(summary = "Проверить промокод для корзины",
            description = "Рассчитывает стоимость товаров по каталогу и применяет промокод так же, как при "
                    + "оформлении заказа, но не резервирует его. Неподходящий промокод отклоняется с кодом 422 "
                    + "и причиной.")
    @PostMapping("/coupons/preview")
    public ApiResponse<CouponPreviewResponse> previewCoupon(@Valid @RequestBody CouponPreviewRequest request) {
        return ApiResponse.success(orderService.previewCoupon(SecurityUtils.currentUser(), request));
    }

    @Operation(summary = "Мои заказы, сначала новые")
    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> myOrders(
            @Parameter(description = "Фильтр по статусу заказа")
            @RequestParam(name = "status", required = false) OrderStatus status,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(orderService.listOrdersOf(SecurityUtils.currentUserId(), status, pageable));
    }

    @Operation(summary = "Заказ с товарами и историей статусов",
            description = "Покупатель видит только свои заказы, администратор — любые.")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> get(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(orderService.getOrder(orderId, SecurityUtils.currentUser()));
    }

    @Operation(summary = "Отменить заказ, который ещё не отправлен",
            description = "Зарезервированный товар освобождается, проданный возвращается на склад; оплата "
                    + "оплаченного заказа возвращается автоматически. Доступно владельцу заказа и администраторам.")
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable("orderId") UUID orderId,
                                             @Valid @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.success(message("order.cancelled"),
                orderService.cancelOrder(orderId, SecurityUtils.currentUser(), reason));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}

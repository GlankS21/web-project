package com.shop.inventory.controller;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.security.SecurityUtils;
import com.shop.inventory.config.OpenApiConfig;
import com.shop.inventory.dto.request.SetStockRequest;
import com.shop.inventory.dto.request.StockAdjustmentRequest;
import com.shop.inventory.dto.response.InventoryItemResponse;
import com.shop.inventory.dto.response.ReservationResponse;
import com.shop.inventory.dto.response.StockMovementResponse;
import com.shop.inventory.service.InventoryService;
import com.shop.inventory.service.StockReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Success messages are resolved from {@code messages_ru.properties}.
 */
@Tag(name = "Управление складом",
        description = "Остатки, корректировки, журнал движения и резервы товара (только для администратора)")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryAdminController {

    private final InventoryService inventoryService;
    private final StockReservationService stockReservationService;
    private final MessageSource messageSource;

    @Operation(summary = "Список складских записей")
    @GetMapping
    public ApiResponse<PageResponse<InventoryItemResponse>> list(
            @Parameter(description = "Только записи, у которых доступный остаток не выше порога «мало на складе»")
            @RequestParam(name = "lowStock", defaultValue = "false") boolean lowStock,
            @ParameterObject
            @PageableDefault(sort = "productId", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(inventoryService.listItems(lowStock, pageable));
    }

    @Operation(summary = "Складская запись товара")
    @GetMapping("/{productId}")
    public ApiResponse<InventoryItemResponse> get(@PathVariable("productId") Long productId) {
        return ApiResponse.success(inventoryService.getItem(productId));
    }

    @Operation(summary = "Задать фактический остаток (инвентаризация)")
    @PutMapping("/{productId}")
    public ApiResponse<InventoryItemResponse> setStock(@PathVariable("productId") Long productId,
                                                       @Valid @RequestBody SetStockRequest request) {
        return ApiResponse.success(message("inventory.stock-updated"),
                inventoryService.setStock(productId, request, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Добавить или списать единицы товара (поступление, брак и т. п.)")
    @PostMapping("/{productId}/adjustments")
    public ApiResponse<InventoryItemResponse> adjust(@PathVariable("productId") Long productId,
                                                     @Valid @RequestBody StockAdjustmentRequest request) {
        return ApiResponse.success(message("inventory.stock-adjusted"),
                inventoryService.adjustStock(productId, request, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Журнал движения товара, сначала новые записи")
    @GetMapping("/{productId}/movements")
    public ApiResponse<PageResponse<StockMovementResponse>> movements(
            @PathVariable("productId") Long productId,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(inventoryService.listMovements(productId, pageable));
    }

    @Operation(summary = "Резерв товара по заказу")
    @GetMapping("/reservations/{orderId}")
    public ApiResponse<ReservationResponse> reservation(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(stockReservationService.getReservation(orderId));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}

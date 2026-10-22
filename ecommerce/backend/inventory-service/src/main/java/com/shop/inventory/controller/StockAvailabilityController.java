package com.shop.inventory.controller;

import com.shop.common.dto.ApiResponse;
import com.shop.inventory.dto.response.StockAvailabilityResponse;
import com.shop.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Наличие товара", description = "Публичный доступ: сколько товара ещё можно заказать")
@RestController
@RequestMapping("/api/inventory/availability")
@RequiredArgsConstructor
public class StockAvailabilityController {

    private final InventoryService inventoryService;

    @Operation(summary = "Доступное количество нескольких товаров (не более 100)",
            description = "Пример: ?productIds=1,2,3. Для неизвестных товаров возвращается доступное количество 0.")
    @GetMapping
    public ApiResponse<List<StockAvailabilityResponse>> availability(
            @RequestParam(name = "productIds") List<Long> productIds) {
        return ApiResponse.success(inventoryService.getAvailability(productIds));
    }
}

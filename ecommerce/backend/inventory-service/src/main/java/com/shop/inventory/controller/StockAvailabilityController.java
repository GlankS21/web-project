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

@Tag(name = "Stock availability", description = "Public: what can still be ordered")
@RestController
@RequestMapping("/api/inventory/availability")
@RequiredArgsConstructor
public class StockAvailabilityController {

    private final InventoryService inventoryService;

    @Operation(summary = "Available quantity of several products (at most 100)",
            description = "Example: ?productIds=1,2,3. Unknown products are reported with 0 available.")
    @GetMapping
    public ApiResponse<List<StockAvailabilityResponse>> availability(
            @RequestParam(name = "productIds") List<Long> productIds) {
        return ApiResponse.success(inventoryService.getAvailability(productIds));
    }
}

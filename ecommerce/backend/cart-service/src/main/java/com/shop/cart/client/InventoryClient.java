package com.shop.cart.client;

import com.shop.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Public stock availability of inventory-service, resolved through Eureka.
 */
@FeignClient(name = "inventory-service", path = "/api/inventory", fallbackFactory = InventoryClientFallbackFactory.class)
public interface InventoryClient {

    /**
     * Availability in the requested order; products without stock record are reported with 0 available.
     */
    @GetMapping("/availability")
    ApiResponse<List<StockAvailability>> getAvailability(@RequestParam("productIds") List<Long> productIds);
}

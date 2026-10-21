package com.shop.order.client;

import com.shop.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Catalogue of product-service, resolved through Eureka. The endpoint is public and returns only
 * ACTIVE products for anonymous callers, which is exactly what can be ordered.
 */
@FeignClient(name = "product-service", path = "/api/products", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    int MAX_BATCH_SIZE = 100;

    /**
     * Products in the requested order; unknown or unpublished ids are left out.
     */
    @GetMapping("/by-ids")
    ApiResponse<List<ProductSummary>> getProductsByIds(@RequestParam("ids") List<Long> ids);
}

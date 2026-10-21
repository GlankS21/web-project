package com.shop.cart.client;

import com.shop.common.dto.ApiResponse;
import com.shop.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Current product data and stock seen by the cart, read from product-service and inventory-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCatalog {

    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    /**
     * Products that can be sold (ACTIVE, with a price), by id; others are missing from the map.
     *
     * @throws ServiceUnavailableException when product-service cannot be reached
     */
    public Map<Long, ProductSummary> findSellableProducts(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductSummary> products = data(productClient.getProductsByIds(List.copyOf(productIds)));
        if (products == null) {
            throw new ServiceUnavailableException("The product catalogue returned no data, please retry");
        }
        return products.stream()
                .filter(product -> product.price() != null && product.price().signum() > 0)
                .collect(Collectors.toMap(ProductSummary::id, Function.identity(), (first, second) -> first));
    }

    /**
     * Available quantity by product id, or empty when inventory-service cannot be reached: callers
     * then treat the stock as unknown rather than failing.
     */
    public Optional<Map<Long, Integer>> findAvailability(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Optional.of(Map.of());
        }
        try {
            List<StockAvailability> availability = data(inventoryClient.getAvailability(List.copyOf(productIds)));
            if (availability == null) {
                return Optional.empty();
            }
            return Optional.of(availability.stream().collect(Collectors.toMap(
                    StockAvailability::productId, StockAvailability::availableQuantity, (first, second) -> first)));
        } catch (ServiceUnavailableException ex) {
            log.warn("Stock availability unknown for products {}: {}", productIds, ex.getMessage());
            return Optional.empty();
        }
    }

    private static <T> T data(ApiResponse<T> response) {
        return response == null ? null : response.getData();
    }
}

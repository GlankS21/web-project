package com.shop.inventory.service;

import com.shop.common.dto.PageResponse;
import com.shop.common.event.ProductCreatedEvent;
import com.shop.inventory.dto.request.SetStockRequest;
import com.shop.inventory.dto.request.StockAdjustmentRequest;
import com.shop.inventory.dto.response.InventoryItemResponse;
import com.shop.inventory.dto.response.StockAvailabilityResponse;
import com.shop.inventory.dto.response.StockMovementResponse;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Stock records and manual stock management. Every change is written to the stock ledger.
 */
public interface InventoryService {

    int MAX_AVAILABILITY_BATCH_SIZE = 100;

    PageResponse<InventoryItemResponse> listItems(boolean lowStockOnly, Pageable pageable);

    InventoryItemResponse getItem(Long productId);

    /**
     * Availability in the order of the requested ids; a product without stock record has 0 available.
     */
    List<StockAvailabilityResponse> getAvailability(Collection<Long> productIds);

    /**
     * Adds or removes units. Creates the stock record when it does not exist yet (the
     * {@code product.created} event may not have been processed).
     */
    InventoryItemResponse adjustStock(Long productId, StockAdjustmentRequest request, UUID actorId);

    /**
     * Sets the counted on-hand quantity (stock take); the difference is recorded as an adjustment.
     */
    InventoryItemResponse setStock(Long productId, SetStockRequest request, UUID actorId);

    PageResponse<StockMovementResponse> listMovements(Long productId, Pageable pageable);

    /**
     * Idempotent handler of {@code product.created}: opens an empty stock record, or fills in the
     * SKU of a record created before the event arrived.
     */
    void registerProduct(ProductCreatedEvent event);
}

package com.shop.inventory.service.impl;

import com.shop.common.dto.PageResponse;
import com.shop.common.event.ProductCreatedEvent;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.inventory.config.InventoryProperties;
import com.shop.inventory.dto.request.SetStockRequest;
import com.shop.inventory.dto.request.StockAdjustmentRequest;
import com.shop.inventory.dto.response.InventoryItemResponse;
import com.shop.inventory.dto.response.StockAvailabilityResponse;
import com.shop.inventory.dto.response.StockMovementResponse;
import com.shop.inventory.entity.InventoryItem;
import com.shop.inventory.entity.MovementType;
import com.shop.inventory.entity.StockMovement;
import com.shop.inventory.mapper.InventoryMapper;
import com.shop.inventory.repository.InventoryItemRepository;
import com.shop.inventory.repository.StockMovementRepository;
import com.shop.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryMapper inventoryMapper;
    private final InventoryProperties properties;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> listItems(boolean lowStockOnly, Pageable pageable) {
        Page<InventoryItem> page = lowStockOnly
                ? inventoryItemRepository.findLowStock(pageable)
                : inventoryItemRepository.findAll(pageable);
        return PageResponse.from(page, inventoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(Long productId) {
        return inventoryItemRepository.findById(productId)
                .map(inventoryMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Stock record", "productId", productId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockAvailabilityResponse> getAvailability(Collection<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_AVAILABILITY_BATCH_SIZE) {
            throw new BadRequestException("At most %d product ids can be requested at once"
                    .formatted(MAX_AVAILABILITY_BATCH_SIZE));
        }
        Map<Long, InventoryItem> byId = inventoryItemRepository.findAllByProductIdIn(ids).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
        return ids.stream()
                .map(id -> byId.containsKey(id)
                        ? inventoryMapper.toAvailability(byId.get(id))
                        : new StockAvailabilityResponse(id, 0, false))
                .toList();
    }

    @Override
    @Transactional
    public InventoryItemResponse adjustStock(Long productId, StockAdjustmentRequest request, UUID actorId) {
        int change = request.quantityChange();
        if (change == 0) {
            throw new BadRequestException("quantityChange must not be 0");
        }
        InventoryItem item = findForUpdateOrNew(productId);
        item.adjust(change);
        return save(item, change, request.reason(), actorId);
    }

    @Override
    @Transactional
    public InventoryItemResponse setStock(Long productId, SetStockRequest request, UUID actorId) {
        InventoryItem item = findForUpdateOrNew(productId);
        int change = request.quantityOnHand() - item.getQuantityOnHand();
        item.adjust(change);
        if (request.lowStockThreshold() != null) {
            item.setLowStockThreshold(request.lowStockThreshold());
        }
        String reason = request.reason() != null ? request.reason() : "Stock count";
        return save(item, change, reason, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> listMovements(Long productId, Pageable pageable) {
        if (!inventoryItemRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Stock record", "productId", productId);
        }
        return PageResponse.from(stockMovementRepository.findAllByProductId(productId, pageable),
                inventoryMapper::toResponse);
    }

    @Override
    @Transactional
    public void registerProduct(ProductCreatedEvent event) {
        inventoryItemRepository.findById(event.productId()).ifPresentOrElse(
                existing -> {
                    if (existing.getSku() == null) {
                        existing.setSku(event.sku());
                        log.info("Filled SKU {} of stock record {} from product event", event.sku(), event.productId());
                    } else {
                        log.debug("Stock record {} already exists, product event ignored", event.productId());
                    }
                },
                () -> {
                    inventoryItemRepository.save(newItem(event.productId(), event.sku()));
                    log.info("Opened stock record for product {} (SKU {})", event.productId(), event.sku());
                });
    }

    private InventoryItem findForUpdateOrNew(Long productId) {
        return inventoryItemRepository.findForUpdate(productId).orElseGet(() -> {
            log.info("Creating missing stock record for product {} on first adjustment", productId);
            return newItem(productId, null);
        });
    }

    private InventoryItem newItem(Long productId, String sku) {
        return InventoryItem.builder()
                .productId(productId)
                .sku(sku)
                .lowStockThreshold(properties.defaultLowStockThreshold())
                .build();
    }

    private InventoryItemResponse save(InventoryItem item, int onHandChange, String reason, UUID actorId) {
        InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
        if (onHandChange != 0) {
            stockMovementRepository.save(StockMovement.of(saved, MovementType.ADJUSTMENT, onHandChange, 0,
                    null, reason, actorId));
            log.info("Stock of product {} adjusted by {} to {} by {}", saved.getProductId(), onHandChange,
                    saved.getQuantityOnHand(), actorId);
        }
        return inventoryMapper.toResponse(saved);
    }
}

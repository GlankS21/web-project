package com.shop.inventory.service.impl;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.event.ProductCreatedEvent;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.BusinessException;
import com.shop.inventory.config.InventoryProperties;
import com.shop.inventory.dto.request.SetStockRequest;
import com.shop.inventory.dto.request.StockAdjustmentRequest;
import com.shop.inventory.dto.response.InventoryItemResponse;
import com.shop.inventory.dto.response.StockAvailabilityResponse;
import com.shop.inventory.entity.InventoryItem;
import com.shop.inventory.entity.MovementType;
import com.shop.inventory.entity.StockMovement;
import com.shop.inventory.mapper.InventoryMapperImpl;
import com.shop.inventory.repository.InventoryItemRepository;
import com.shop.inventory.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;

    private InventoryServiceImpl service;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(inventoryItemRepository, stockMovementRepository, new InventoryMapperImpl(),
                new InventoryProperties(7, null), new CommonMessagesAutoConfiguration().messageSource());
    }

    @Test
    void adjustStock_recordsMovementWithActor() {
        InventoryItem item = item(1L, 10, 2);
        when(inventoryItemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.saveAndFlush(item)).thenReturn(item);

        InventoryItemResponse response = service.adjustStock(1L, new StockAdjustmentRequest(5, "Received"), adminId);

        assertThat(response.quantityOnHand()).isEqualTo(15);
        assertThat(response.availableQuantity()).isEqualTo(13);
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        assertThat(movement.getValue().getType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(movement.getValue().getOnHandChange()).isEqualTo(5);
        assertThat(movement.getValue().getOnHandAfter()).isEqualTo(15);
        assertThat(movement.getValue().getActorId()).isEqualTo(adminId);
    }

    @Test
    void adjustStock_createsMissingRecordWithDefaultThreshold() {
        when(inventoryItemRepository.findForUpdate(9L)).thenReturn(Optional.empty());
        when(inventoryItemRepository.saveAndFlush(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItemResponse response = service.adjustStock(9L, new StockAdjustmentRequest(3, "First delivery"), adminId);

        assertThat(response.productId()).isEqualTo(9L);
        assertThat(response.quantityOnHand()).isEqualTo(3);
        assertThat(response.lowStockThreshold()).isEqualTo(7);
        assertThat(response.sku()).isNull();
    }

    @Test
    void adjustStock_rejectsZeroAndRemovingReservedUnits() {
        assertThatThrownBy(() -> service.adjustStock(1L, new StockAdjustmentRequest(0, "noop"), adminId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("inventory.error.zero-change");

        when(inventoryItemRepository.findForUpdate(1L)).thenReturn(Optional.of(item(1L, 10, 8)));
        assertThatThrownBy(() -> service.adjustStock(1L, new StockAdjustmentRequest(-3, "Damaged"), adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("inventory.error.below-reserved");
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void setStock_recordsTheDifferenceAndUpdatesThreshold() {
        InventoryItem item = item(1L, 10, 0);
        when(inventoryItemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.saveAndFlush(item)).thenReturn(item);

        service.setStock(1L, new SetStockRequest(7, 3, null), adminId);

        assertThat(item.getQuantityOnHand()).isEqualTo(7);
        assertThat(item.getLowStockThreshold()).isEqualTo(3);
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        assertThat(movement.getValue().getOnHandChange()).isEqualTo(-3);
        assertThat(movement.getValue().getReason()).isEqualTo("Инвентаризация");
    }

    @Test
    void setStock_withSameQuantityWritesNoMovement() {
        InventoryItem item = item(1L, 10, 0);
        when(inventoryItemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.saveAndFlush(item)).thenReturn(item);

        service.setStock(1L, new SetStockRequest(10, 4, "threshold only"), adminId);

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void getAvailability_reportsUnknownProductsAsOutOfStock() {
        when(inventoryItemRepository.findAllByProductIdIn(List.of(2L, 1L))).thenReturn(List.of(item(1L, 5, 5), item(2L, 4, 1)));

        List<StockAvailabilityResponse> result = service.getAvailability(List.of(2L, 1L));

        assertThat(result).containsExactly(
                new StockAvailabilityResponse(2L, 3, true),
                new StockAvailabilityResponse(1L, 0, false));

        when(inventoryItemRepository.findAllByProductIdIn(List.of(99L))).thenReturn(List.of());
        assertThat(service.getAvailability(List.of(99L))).containsExactly(new StockAvailabilityResponse(99L, 0, false));
    }

    @Test
    void registerProduct_createsRecordOrFillsMissingSku() {
        when(inventoryItemRepository.findById(5L)).thenReturn(Optional.empty());
        service.registerProduct(new ProductCreatedEvent(UUID.randomUUID(), Instant.now(), 5L, "SKU-5", "Five"));
        ArgumentCaptor<InventoryItem> created = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository).save(created.capture());
        assertThat(created.getValue().getSku()).isEqualTo("SKU-5");
        assertThat(created.getValue().getQuantityOnHand()).isZero();

        InventoryItem lazy = item(6L, 4, 0);
        when(inventoryItemRepository.findById(6L)).thenReturn(Optional.of(lazy));
        service.registerProduct(new ProductCreatedEvent(UUID.randomUUID(), Instant.now(), 6L, "SKU-6", "Six"));
        assertThat(lazy.getSku()).isEqualTo("SKU-6");
        assertThat(lazy.getQuantityOnHand()).isEqualTo(4);
    }

    private InventoryItem item(Long productId, int onHand, int reserved) {
        return InventoryItem.builder()
                .productId(productId)
                .quantityOnHand(onHand)
                .quantityReserved(reserved)
                .lowStockThreshold(5)
                .version(0L)
                .build();
    }
}

package com.shop.inventory.mapper;

import com.shop.inventory.dto.response.InventoryItemResponse;
import com.shop.inventory.dto.response.ReservationResponse;
import com.shop.inventory.dto.response.StockAvailabilityResponse;
import com.shop.inventory.dto.response.StockMovementResponse;
import com.shop.inventory.entity.InventoryItem;
import com.shop.inventory.entity.StockMovement;
import com.shop.inventory.entity.StockReservation;
import com.shop.inventory.entity.StockReservationItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface InventoryMapper {

    @Mapping(target = "availableQuantity", expression = "java(item.availableQuantity())")
    @Mapping(target = "lowStock", expression = "java(item.isLowStock())")
    InventoryItemResponse toResponse(InventoryItem item);

    @Mapping(target = "availableQuantity", expression = "java(item.availableQuantity())")
    @Mapping(target = "inStock", expression = "java(item.availableQuantity() > 0)")
    StockAvailabilityResponse toAvailability(InventoryItem item);

    StockMovementResponse toResponse(StockMovement movement);

    ReservationResponse toResponse(StockReservation reservation);

    ReservationResponse.Item toResponse(StockReservationItem item);
}

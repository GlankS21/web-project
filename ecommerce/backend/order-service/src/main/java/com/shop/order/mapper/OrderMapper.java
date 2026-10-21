package com.shop.order.mapper;

import com.shop.order.dto.request.ShippingAddressRequest;
import com.shop.order.dto.response.OrderItemResponse;
import com.shop.order.dto.response.OrderResponse;
import com.shop.order.dto.response.OrderStatusHistoryResponse;
import com.shop.order.dto.response.OrderSummaryResponse;
import com.shop.order.dto.response.ShippingAddressResponse;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderItem;
import com.shop.order.entity.OrderStatusHistory;
import com.shop.order.entity.ShippingAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    @Mapping(target = "itemCount", expression = "java(order.getItems().size())")
    @Mapping(target = "firstItemName",
            expression = "java(order.getItems().isEmpty() ? null : order.getItems().getFirst().getProductName())")
    @Mapping(target = "firstItemThumbnailUrl",
            expression = "java(order.getItems().isEmpty() ? null : order.getItems().getFirst().getThumbnailUrl())")
    OrderSummaryResponse toSummary(Order order);

    OrderItemResponse toResponse(OrderItem item);

    OrderStatusHistoryResponse toResponse(OrderStatusHistory history);

    ShippingAddressResponse toResponse(ShippingAddress address);

    ShippingAddress toEntity(ShippingAddressRequest request);
}

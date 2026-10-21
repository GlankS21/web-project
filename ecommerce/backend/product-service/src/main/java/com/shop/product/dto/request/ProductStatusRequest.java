package com.shop.product.dto.request;

import com.shop.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull ProductStatus status) {
}

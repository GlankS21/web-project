package com.shop.order.dto.request;

import com.shop.order.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

        @Schema(description = "Usually the content of the cart. The ordered quantities are then removed from the cart.")
        @NotEmpty @Size(max = 100)
        List<@NotNull @Valid OrderItemRequest> items,

        @NotNull @Valid
        ShippingAddressRequest shippingAddress,

        @Schema(example = "CARD")
        @NotNull
        PaymentMethod paymentMethod,

        @Schema(example = "Please call before delivery")
        @Size(max = 500)
        String note
) {
}

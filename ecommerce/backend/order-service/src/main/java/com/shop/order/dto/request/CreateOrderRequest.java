package com.shop.order.dto.request;

import com.shop.order.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

        @Schema(description = "Обычно содержимое корзины; заказанные количества затем удаляются из корзины.")
        @NotEmpty @Size(max = 100)
        List<@NotNull @Valid OrderItemRequest> items,

        @Schema(description = "Адрес доставки")
        @NotNull @Valid
        ShippingAddressRequest shippingAddress,

        @Schema(description = "Способ оплаты", example = "ONLINE")
        @NotNull
        PaymentMethod paymentMethod,

        @Schema(description = "Комментарий к заказу", example = "Позвоните, пожалуйста, перед доставкой")
        @Size(max = 500)
        String note,

        @Schema(description = "Промокод (необязательно), регистр не учитывается; пусто — без промокода", example = "WELCOME10")
        @Size(max = 30)
        String couponCode
) {
}

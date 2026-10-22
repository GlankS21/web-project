package com.shop.cart.controller;

import com.shop.cart.dto.request.AddCartItemRequest;
import com.shop.cart.dto.request.UpdateCartItemRequest;
import com.shop.cart.dto.response.CartResponse;
import com.shop.cart.service.CartService;
import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Корзина", description = "Корзина текущего покупателя")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    static final String ITEM_ADDED = "cart.item-added";
    static final String CART_UPDATED = "cart.updated";
    static final String ITEM_REMOVED = "cart.item-removed";
    static final String CART_CLEARED = "cart.cleared";

    private final CartService cartService;
    private final MessageSource messageSource;

    @Operation(summary = "Моя корзина с актуальными ценами и наличием")
    @GetMapping
    public ApiResponse<CartResponse> get() {
        return ApiResponse.success(cartService.getCart(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Добавить товар в корзину",
            description = "Товар должен быть в продаже, а итоговое количество - в наличии (если наличие удаётся проверить).")
    @PostMapping("/items")
    public ApiResponse<CartResponse> add(@Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.success(message(ITEM_ADDED),
                cartService.addItem(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Изменить количество товара в корзине")
    @PutMapping("/items/{productId}")
    public ApiResponse<CartResponse> update(
            @Parameter(description = "Идентификатор товара", example = "7") @PathVariable("productId") Long productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.success(message(CART_UPDATED),
                cartService.updateItem(SecurityUtils.currentUserId(), productId, request.quantity()));
    }

    @Operation(summary = "Удалить товар из корзины")
    @DeleteMapping("/items/{productId}")
    public ApiResponse<CartResponse> remove(
            @Parameter(description = "Идентификатор товара", example = "7") @PathVariable("productId") Long productId) {
        return ApiResponse.success(message(ITEM_REMOVED),
                cartService.removeItem(SecurityUtils.currentUserId(), productId));
    }

    @Operation(summary = "Очистить корзину")
    @DeleteMapping
    public ApiResponse<Void> clear() {
        cartService.clearCart(SecurityUtils.currentUserId());
        return ApiResponse.message(message(CART_CLEARED));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}

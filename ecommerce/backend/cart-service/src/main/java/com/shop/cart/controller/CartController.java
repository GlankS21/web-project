package com.shop.cart.controller;

import com.shop.cart.dto.request.AddCartItemRequest;
import com.shop.cart.dto.request.UpdateCartItemRequest;
import com.shop.cart.dto.response.CartResponse;
import com.shop.cart.service.CartService;
import com.shop.common.dto.ApiResponse;
import com.shop.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart", description = "Shopping cart of the authenticated customer")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "My cart with current prices and stock")
    @GetMapping
    public ApiResponse<CartResponse> get() {
        return ApiResponse.success(cartService.getCart(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Add units of a product",
            description = "The product must be on sale and the total quantity in stock (when stock can be checked).")
    @PostMapping("/items")
    public ApiResponse<CartResponse> add(@Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.success("Product added to the cart",
                cartService.addItem(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Change the quantity of a line")
    @PutMapping("/items/{productId}")
    public ApiResponse<CartResponse> update(@PathVariable("productId") Long productId,
                                            @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.success("Cart updated",
                cartService.updateItem(SecurityUtils.currentUserId(), productId, request.quantity()));
    }

    @Operation(summary = "Remove a line")
    @DeleteMapping("/items/{productId}")
    public ApiResponse<CartResponse> remove(@PathVariable("productId") Long productId) {
        return ApiResponse.success("Product removed from the cart",
                cartService.removeItem(SecurityUtils.currentUserId(), productId));
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping
    public ApiResponse<Void> clear() {
        cartService.clearCart(SecurityUtils.currentUserId());
        return ApiResponse.message("Cart cleared");
    }
}

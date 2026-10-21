package com.shop.user.controller;

import com.shop.common.dto.ApiResponse;
import com.shop.common.security.AuthenticatedUser;
import com.shop.common.security.SecurityUtils;
import com.shop.user.dto.request.AddressRequest;
import com.shop.user.dto.response.AddressResponse;
import com.shop.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Address book", description = "Shipping addresses of the authenticated user")
@RestController
@RequestMapping("/api/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "List my addresses (default first)")
    @GetMapping
    public ApiResponse<List<AddressResponse>> list() {
        return ApiResponse.success(addressService.listAddresses(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Get one of my addresses")
    @GetMapping("/{addressId}")
    public ApiResponse<AddressResponse> get(@PathVariable("addressId") UUID addressId) {
        return ApiResponse.success(addressService.getAddress(SecurityUtils.currentUserId(), addressId));
    }

    @Operation(summary = "Add an address")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressResponse> create(@Valid @RequestBody AddressRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return ApiResponse.success("Address created", addressService.createAddress(user.id(), user.email(), request));
    }

    @Operation(summary = "Update an address")
    @PutMapping("/{addressId}")
    public ApiResponse<AddressResponse> update(@PathVariable("addressId") UUID addressId,
                                               @Valid @RequestBody AddressRequest request) {
        return ApiResponse.success("Address updated",
                addressService.updateAddress(SecurityUtils.currentUserId(), addressId, request));
    }

    @Operation(summary = "Delete an address (the most recent remaining one becomes default if needed)")
    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(@PathVariable("addressId") UUID addressId) {
        addressService.deleteAddress(SecurityUtils.currentUserId(), addressId);
        return ApiResponse.message("Address deleted");
    }

    @Operation(summary = "Make an address the default one")
    @PatchMapping("/{addressId}/default")
    public ApiResponse<AddressResponse> setDefault(@PathVariable("addressId") UUID addressId) {
        return ApiResponse.success("Default address updated",
                addressService.setDefaultAddress(SecurityUtils.currentUserId(), addressId));
    }
}

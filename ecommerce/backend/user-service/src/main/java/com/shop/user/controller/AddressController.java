package com.shop.user.controller;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.security.AuthenticatedUser;
import com.shop.common.security.SecurityUtils;
import com.shop.user.dto.request.AddressRequest;
import com.shop.user.dto.response.AddressResponse;
import com.shop.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
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

@Tag(name = "Адресная книга", description = "Адреса доставки текущего пользователя")
@RestController
@RequestMapping("/api/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    static final String ADDRESS_CREATED = "address.created";
    static final String ADDRESS_UPDATED = "address.updated";
    static final String ADDRESS_DELETED = "address.deleted";
    static final String DEFAULT_ADDRESS_UPDATED = "address.default-updated";

    private final AddressService addressService;
    private final MessageSource messageSource;

    @Operation(summary = "Мои адреса (адрес по умолчанию первым)")
    @GetMapping
    public ApiResponse<List<AddressResponse>> list() {
        return ApiResponse.success(addressService.listAddresses(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Получить мой адрес")
    @GetMapping("/{addressId}")
    public ApiResponse<AddressResponse> get(
            @Parameter(description = "Идентификатор адреса") @PathVariable("addressId") UUID addressId) {
        return ApiResponse.success(addressService.getAddress(SecurityUtils.currentUserId(), addressId));
    }

    @Operation(summary = "Добавить адрес", description = "Без countryCode адрес сохраняется с кодом страны RU")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressResponse> create(@Valid @RequestBody AddressRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return ApiResponse.success(message(ADDRESS_CREATED), addressService.createAddress(user.id(), user.email(), request));
    }

    @Operation(summary = "Изменить адрес", description = "Без countryCode адрес сохраняется с кодом страны RU")
    @PutMapping("/{addressId}")
    public ApiResponse<AddressResponse> update(
            @Parameter(description = "Идентификатор адреса") @PathVariable("addressId") UUID addressId,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.success(message(ADDRESS_UPDATED),
                addressService.updateAddress(SecurityUtils.currentUserId(), addressId, request));
    }

    @Operation(summary = "Удалить адрес (при необходимости адресом по умолчанию становится самый новый из оставшихся)")
    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(
            @Parameter(description = "Идентификатор адреса") @PathVariable("addressId") UUID addressId) {
        addressService.deleteAddress(SecurityUtils.currentUserId(), addressId);
        return ApiResponse.message(message(ADDRESS_DELETED));
    }

    @Operation(summary = "Сделать адрес адресом по умолчанию")
    @PatchMapping("/{addressId}/default")
    public ApiResponse<AddressResponse> setDefault(
            @Parameter(description = "Идентификатор адреса") @PathVariable("addressId") UUID addressId) {
        return ApiResponse.success(message(DEFAULT_ADDRESS_UPDATED),
                addressService.setDefaultAddress(SecurityUtils.currentUserId(), addressId));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}

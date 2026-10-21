package com.shop.user.service;

import com.shop.user.dto.request.AddressRequest;
import com.shop.user.dto.response.AddressResponse;

import java.util.List;
import java.util.UUID;

/**
 * Address book of the calling user. Every operation is scoped by {@code userId}.
 */
public interface AddressService {

    int MAX_ADDRESSES_PER_USER = 10;

    List<AddressResponse> listAddresses(UUID userId);

    AddressResponse getAddress(UUID userId, UUID addressId);

    AddressResponse createAddress(UUID userId, String email, AddressRequest request);

    AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request);

    void deleteAddress(UUID userId, UUID addressId);

    AddressResponse setDefaultAddress(UUID userId, UUID addressId);
}

package com.shop.user.service.impl;

import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.user.dto.request.AddressRequest;
import com.shop.user.dto.response.AddressResponse;
import com.shop.user.entity.Address;
import com.shop.user.mapper.AddressMapper;
import com.shop.user.repository.AddressRepository;
import com.shop.user.repository.UserProfileRepository;
import com.shop.user.service.AddressService;
import com.shop.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileService userProfileService;
    private final AddressMapper addressMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(UUID userId) {
        return addressMapper.toResponses(addressRepository.findAllByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddress(UUID userId, UUID addressId) {
        return addressMapper.toResponse(findOwned(userId, addressId));
    }

    @Override
    @Transactional
    public AddressResponse createAddress(UUID userId, String email, AddressRequest request) {
        userProfileService.getOrCreateProfile(userId, email);
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES_PER_USER) {
            throw new BusinessException("An address book can hold at most %d addresses".formatted(MAX_ADDRESSES_PER_USER));
        }

        Address address = addressMapper.toEntity(request);
        address.setUser(userProfileRepository.getReferenceById(userId));
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.defaultAddress());
        if (makeDefault && existing > 0) {
            addressRepository.clearDefaultForUser(userId);
        }
        address.setDefaultAddress(makeDefault);
        return addressMapper.toResponse(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request) {
        Address address = findOwned(userId, addressId);
        addressMapper.updateFromRequest(request, address);
        // Un-setting the default is done by choosing another default address, never implicitly.
        if (Boolean.TRUE.equals(request.defaultAddress()) && !address.isDefaultAddress()) {
            addressRepository.clearDefaultForUser(userId);
            address.setDefaultAddress(true);
        }
        return addressMapper.toResponse(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Address address = findOwned(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        // Hibernate runs updates before deletes at flush time: delete first so that promoting
        // another default cannot collide with the removed row on the unique index.
        addressRepository.flush();
        if (wasDefault) {
            addressRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(UUID userId, UUID addressId) {
        Address address = findOwned(userId, addressId);
        if (!address.isDefaultAddress()) {
            addressRepository.clearDefaultForUser(userId);
            address.setDefaultAddress(true);
        }
        return addressMapper.toResponse(addressRepository.saveAndFlush(address));
    }

    private Address findOwned(UUID userId, UUID addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
    }
}

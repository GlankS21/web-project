package com.shop.user.service.impl;

import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.user.dto.request.AddressRequest;
import com.shop.user.dto.response.AddressResponse;
import com.shop.user.entity.Address;
import com.shop.user.entity.UserProfile;
import com.shop.user.mapper.AddressMapperImpl;
import com.shop.user.repository.AddressRepository;
import com.shop.user.repository.UserProfileRepository;
import com.shop.user.service.AddressService;
import com.shop.user.service.UserProfileService;
import com.shop.user.util.RussianPlural;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserProfileService userProfileService;

    private AddressServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AddressServiceImpl(addressRepository, userProfileRepository, userProfileService,
                new AddressMapperImpl());
    }

    @Test
    void createAddress_firstAddressBecomesDefault() {
        when(addressRepository.countByUserId(userId)).thenReturn(0L);
        when(userProfileRepository.getReferenceById(userId)).thenReturn(UserProfile.builder().id(userId).build());
        when(addressRepository.saveAndFlush(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressResponse response = service.createAddress(userId, "jane@example.com", request(false));

        assertThat(response.defaultAddress()).isTrue();
        assertThat(response.city()).isEqualTo("Москва");
        verify(userProfileService).getOrCreateProfile(userId, "jane@example.com");
        verify(addressRepository, never()).clearDefaultForUser(any());
    }

    @Test
    void createAddress_requestedDefaultClearsPreviousDefaultFirst() {
        when(addressRepository.countByUserId(userId)).thenReturn(2L);
        when(addressRepository.saveAndFlush(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressResponse response = service.createAddress(userId, "jane@example.com", request(true));

        assertThat(response.defaultAddress()).isTrue();
        InOrder order = inOrder(addressRepository);
        order.verify(addressRepository).clearDefaultForUser(userId);
        order.verify(addressRepository).saveAndFlush(any(Address.class));
    }

    @Test
    void createAddress_nonDefaultAddressKeepsExistingDefault() {
        when(addressRepository.countByUserId(userId)).thenReturn(1L);
        when(addressRepository.saveAndFlush(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressResponse response = service.createAddress(userId, "jane@example.com", request(false));

        assertThat(response.defaultAddress()).isFalse();
        verify(addressRepository, never()).clearDefaultForUser(any());
    }

    @Test
    void createAddress_rejectsWhenAddressBookIsFull() {
        when(addressRepository.countByUserId(userId)).thenReturn((long) AddressService.MAX_ADDRESSES_PER_USER);

        assertThatThrownBy(() -> service.createAddress(userId, "jane@example.com", request(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AddressServiceImpl.ADDRESS_LIMIT_REACHED)
                .extracting(ex -> ((BusinessException) ex).getMessageArgs())
                .isEqualTo(new Object[]{AddressService.MAX_ADDRESSES_PER_USER, RussianPlural.MANY});
        verify(addressRepository, never()).saveAndFlush(any());
    }

    @Test
    void createAddress_withoutCountryIsInRussia() {
        when(addressRepository.countByUserId(userId)).thenReturn(0L);
        when(userProfileRepository.getReferenceById(userId)).thenReturn(UserProfile.builder().id(userId).build());
        when(addressRepository.saveAndFlush(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        AddressRequest request = new AddressRequest("Иван Петров", "+79161234567", "ул. Тверская, д. 7", null,
                null, null, "Москва", "125009", null, null);

        AddressResponse response = service.createAddress(userId, "ivan@example.ru", request);

        assertThat(response.countryCode()).isEqualTo("RU");
    }

    @Test
    void getAddress_ofAnotherUserIsNotFound() {
        UUID addressId = UUID.randomUUID();
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAddress(userId, addressId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteAddress_defaultAddressPromotesMostRecentRemainingOneAfterFlush() {
        Address toDelete = address(true);
        Address remaining = address(false);
        when(addressRepository.findByIdAndUserId(toDelete.getId(), userId)).thenReturn(Optional.of(toDelete));
        when(addressRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(remaining));

        service.deleteAddress(userId, toDelete.getId());

        InOrder order = inOrder(addressRepository);
        order.verify(addressRepository).delete(toDelete);
        order.verify(addressRepository).flush();
        order.verify(addressRepository).findFirstByUserIdOrderByCreatedAtDesc(userId);
        assertThat(remaining.isDefaultAddress()).isTrue();
    }

    @Test
    void deleteAddress_nonDefaultAddressDoesNotPromoteAnything() {
        Address toDelete = address(false);
        when(addressRepository.findByIdAndUserId(toDelete.getId(), userId)).thenReturn(Optional.of(toDelete));

        service.deleteAddress(userId, toDelete.getId());

        verify(addressRepository).delete(toDelete);
        verify(addressRepository, never()).findFirstByUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void setDefaultAddress_switchesDefault() {
        Address target = address(false);
        when(addressRepository.findByIdAndUserId(target.getId(), userId)).thenReturn(Optional.of(target));
        when(addressRepository.saveAndFlush(target)).thenReturn(target);

        AddressResponse response = service.setDefaultAddress(userId, target.getId());

        assertThat(response.defaultAddress()).isTrue();
        verify(addressRepository).clearDefaultForUser(userId);
    }

    @Test
    void setDefaultAddress_onCurrentDefaultIsNoOp() {
        Address target = address(true);
        when(addressRepository.findByIdAndUserId(target.getId(), userId)).thenReturn(Optional.of(target));
        when(addressRepository.saveAndFlush(target)).thenReturn(target);

        service.setDefaultAddress(userId, target.getId());

        verify(addressRepository, never()).clearDefaultForUser(any());
    }

    private AddressRequest request(boolean makeDefault) {
        return new AddressRequest("Анна Иванова", "+79161234567", "ул. Тверская, д. 7", null, "Тверской",
                "Центральный административный округ", "Москва", "125009", "RU", makeDefault);
    }

    private Address address(boolean isDefault) {
        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setRecipientName("Анна Иванова");
        address.setPhoneNumber("+79161234567");
        address.setAddressLine1("ул. Тверская, д. 7");
        address.setCity("Москва");
        address.setCountryCode("RU");
        address.setDefaultAddress(isDefault);
        return address;
    }
}

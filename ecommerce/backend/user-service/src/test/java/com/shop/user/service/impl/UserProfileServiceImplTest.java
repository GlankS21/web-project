package com.shop.user.service.impl;

import com.shop.common.event.UserRegisteredEvent;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.user.dto.request.UpdateProfileRequest;
import com.shop.user.dto.response.UserProfileResponse;
import com.shop.user.entity.UserProfile;
import com.shop.user.mapper.UserProfileMapperImpl;
import com.shop.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    private UserProfileServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(userProfileRepository, new UserProfileMapperImpl());
    }

    @Test
    void getOrCreateProfile_returnsExistingProfile() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile("Jane", "Doe")));

        UserProfileResponse response = service.getOrCreateProfile(userId, "jane@example.com");

        assertThat(response.firstName()).isEqualTo("Jane");
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void getOrCreateProfile_createsMissingProfileFromTokenEmail() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(userProfileRepository.saveAndFlush(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse response = service.getOrCreateProfile(userId, " Jane@Example.com ");

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.firstName()).isNull();
    }

    @Test
    void updateProfile_copiesEditableFieldsOnly() {
        UserProfile existing = profile("Old", "Name");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userProfileRepository.saveAndFlush(existing)).thenReturn(existing);

        UserProfileResponse response = service.updateProfile(userId, "jane@example.com",
                new UpdateProfileRequest("Jane", "Doe", "+84901234567", LocalDate.of(1995, 4, 20)));

        assertThat(response.firstName()).isEqualTo("Jane");
        assertThat(response.phoneNumber()).isEqualTo("+84901234567");
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(existing.getId()).isEqualTo(userId);
    }

    @Test
    void createProfileFromRegistration_createsNewProfile() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        service.createProfileFromRegistration(event());

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(userId);
        assertThat(saved.getValue().getFirstName()).isEqualTo("Jane");
    }

    @Test
    void createProfileFromRegistration_fillsNamesOfLazilyCreatedProfile() {
        UserProfile lazy = profile(null, null);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(lazy));

        service.createProfileFromRegistration(event());

        assertThat(lazy.getFirstName()).isEqualTo("Jane");
        assertThat(lazy.getLastName()).isEqualTo("Doe");
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void createProfileFromRegistration_isIdempotentForCompleteProfiles() {
        UserProfile complete = profile("Edited", "ByUser");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(complete));

        service.createProfileFromRegistration(event());

        assertThat(complete.getFirstName()).isEqualTo("Edited");
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void getProfile_unknownIdIsNotFound() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(userId)).isInstanceOf(ResourceNotFoundException.class);
    }

    private UserProfile profile(String firstName, String lastName) {
        return UserProfile.builder()
                .id(userId)
                .email("jane@example.com")
                .firstName(firstName)
                .lastName(lastName)
                .version(0L)
                .build();
    }

    private UserRegisteredEvent event() {
        return new UserRegisteredEvent(UUID.randomUUID(), Instant.now(), userId, "jane@example.com", "Jane", "Doe");
    }
}

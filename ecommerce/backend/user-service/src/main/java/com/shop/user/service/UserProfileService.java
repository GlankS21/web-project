package com.shop.user.service;

import com.shop.common.dto.PageResponse;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.user.dto.request.UpdateProfileRequest;
import com.shop.user.dto.response.UserProfileResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserProfileService {

    /**
     * Returns the caller's profile, creating an empty one if the registration event has not
     * been processed yet (or was lost).
     */
    UserProfileResponse getOrCreateProfile(UUID userId, String email);

    UserProfileResponse updateProfile(UUID userId, String email, UpdateProfileRequest request);

    /**
     * Idempotent handler of {@code user.registered}: creates the profile, or fills in the names of
     * a profile that was created lazily before the event arrived.
     */
    void createProfileFromRegistration(UserRegisteredEvent event);

    PageResponse<UserProfileResponse> searchProfiles(String keyword, Pageable pageable);

    UserProfileResponse getProfile(UUID userId);
}

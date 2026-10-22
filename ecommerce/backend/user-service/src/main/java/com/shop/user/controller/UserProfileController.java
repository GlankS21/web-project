package com.shop.user.controller;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.security.AuthenticatedUser;
import com.shop.common.security.SecurityUtils;
import com.shop.user.dto.request.UpdateProfileRequest;
import com.shop.user.dto.response.UserProfileResponse;
import com.shop.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Профиль", description = "Профиль текущего пользователя")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    static final String PROFILE_UPDATED = "profile.updated";

    private final UserProfileService userProfileService;
    private final MessageSource messageSource;

    @Operation(summary = "Мой профиль")
    @GetMapping
    public ApiResponse<UserProfileResponse> getMyProfile() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return ApiResponse.success(userProfileService.getOrCreateProfile(user.id(), user.email()));
    }

    @Operation(summary = "Изменить мой профиль")
    @PutMapping
    public ApiResponse<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return ApiResponse.success(message(PROFILE_UPDATED),
                userProfileService.updateProfile(user.id(), user.email(), request));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}

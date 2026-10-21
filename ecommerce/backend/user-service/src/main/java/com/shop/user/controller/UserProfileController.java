package com.shop.user.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile", description = "Profile of the authenticated user")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Get my profile")
    @GetMapping
    public ApiResponse<UserProfileResponse> getMyProfile() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return ApiResponse.success(userProfileService.getOrCreateProfile(user.id(), user.email()));
    }

    @Operation(summary = "Update my profile")
    @PutMapping
    public ApiResponse<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return ApiResponse.success("Profile updated",
                userProfileService.updateProfile(user.id(), user.email(), request));
    }
}

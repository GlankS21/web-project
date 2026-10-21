package com.shop.user.controller;

import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.user.dto.response.UserProfileResponse;
import com.shop.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User administration", description = "Browse user profiles (admin only)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Search profiles by email or name")
    @GetMapping
    public ApiResponse<PageResponse<UserProfileResponse>> search(
            @Parameter(description = "Part of the email, first name or last name")
            @RequestParam(name = "keyword", required = false) String keyword,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(userProfileService.searchProfiles(keyword, pageable));
    }

    @Operation(summary = "Get a profile")
    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> get(@PathVariable("userId") UUID userId) {
        return ApiResponse.success(userProfileService.getProfile(userId));
    }
}

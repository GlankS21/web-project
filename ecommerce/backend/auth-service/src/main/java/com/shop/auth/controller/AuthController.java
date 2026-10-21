package com.shop.auth.controller;

import com.shop.auth.config.OpenApiConfig;
import com.shop.auth.dto.request.ChangePasswordRequest;
import com.shop.auth.dto.request.LoginRequest;
import com.shop.auth.dto.request.RefreshTokenRequest;
import com.shop.auth.dto.request.RegisterRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.dto.response.AuthResponse;
import com.shop.auth.service.AuthService;
import com.shop.common.dto.ApiResponse;
import com.shop.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Registration, login and session management")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a customer account and log in")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("Account created", authService.register(request));
    }

    @Operation(summary = "Log in with email and password")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "Exchange a refresh token for a new token pair (rotation)")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @Operation(summary = "Revoke the given refresh token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ApiResponse.message("Logged out");
    }

    @Operation(summary = "Revoke every session of the current account",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll() {
        authService.logoutAll(SecurityUtils.currentUserId());
        return ApiResponse.message("All sessions have been revoked");
    }

    @Operation(summary = "Current account",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @GetMapping("/me")
    public ApiResponse<AccountResponse> me() {
        return ApiResponse.success(authService.getAccount(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Change the password of the current account (revokes all sessions)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.currentUserId(), request);
        return ApiResponse.message("Password changed, please log in again");
    }
}

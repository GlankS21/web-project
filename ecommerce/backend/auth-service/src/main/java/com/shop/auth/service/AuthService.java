package com.shop.auth.service;

import com.shop.auth.dto.request.ChangePasswordRequest;
import com.shop.auth.dto.request.LoginRequest;
import com.shop.auth.dto.request.RefreshTokenRequest;
import com.shop.auth.dto.request.RegisterRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.dto.response.AuthResponse;

import java.util.UUID;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    void logoutAll(UUID accountId);

    AccountResponse getAccount(UUID accountId);

    void changePassword(UUID accountId, ChangePasswordRequest request);
}

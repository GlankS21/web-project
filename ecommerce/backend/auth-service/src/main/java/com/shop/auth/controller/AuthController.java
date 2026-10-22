package com.shop.auth.controller;

import com.shop.auth.config.OpenApiConfig;
import com.shop.auth.dto.request.ChangePasswordRequest;
import com.shop.auth.dto.request.ForgotPasswordRequest;
import com.shop.auth.dto.request.LoginRequest;
import com.shop.auth.dto.request.RefreshTokenRequest;
import com.shop.auth.dto.request.RegisterRequest;
import com.shop.auth.dto.request.ResetPasswordRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.dto.response.AuthResponse;
import com.shop.auth.service.AuthService;
import com.shop.auth.service.PasswordResetService;
import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Аутентификация", description = "Регистрация, вход и управление сеансами")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String REGISTERED = "auth.registered";
    static final String LOGGED_OUT = "auth.logged-out";
    static final String LOGGED_OUT_ALL = "auth.logged-out-all";
    static final String PASSWORD_CHANGED = "auth.password.changed";
    static final String PASSWORD_RESET_REQUESTED = "auth.password.reset-requested";
    static final String PASSWORD_RESET_DONE = "auth.password.reset-done";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final MessageSource messageSource;

    @Operation(summary = "Зарегистрировать аккаунт покупателя и войти в него")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(localize(REGISTERED), authService.register(request));
    }

    @Operation(summary = "Войти по e-mail и паролю")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "Обменять токен обновления на новую пару токенов (ротация)")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @Operation(summary = "Отозвать указанный токен обновления (выход)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ApiResponse.message(localize(LOGGED_OUT));
    }

    @Operation(summary = "Завершить все сеансы текущего аккаунта",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll() {
        authService.logoutAll(SecurityUtils.currentUserId());
        return ApiResponse.message(localize(LOGGED_OUT_ALL));
    }

    @Operation(summary = "Текущий аккаунт",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @GetMapping("/me")
    public ApiResponse<AccountResponse> me() {
        return ApiResponse.success(authService.getAccount(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Сменить пароль текущего аккаунта (завершает все сеансы)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.currentUserId(), request);
        return ApiResponse.message(localize(PASSWORD_CHANGED));
    }

    @Operation(summary = "Запросить ссылку для сброса пароля по e-mail",
            description = "Ответ всегда одинаков, независимо от того, есть ли аккаунт с этим e-mail")
    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ApiResponse.message(localize(PASSWORD_RESET_REQUESTED));
    }

    @Operation(summary = "Задать новый пароль по токену из ссылки сброса (завершает все сеансы)")
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ApiResponse.message(localize(PASSWORD_RESET_DONE));
    }

    /**
     * Success messages are not translated by the exception handler, so they are resolved here.
     */
    private String localize(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}

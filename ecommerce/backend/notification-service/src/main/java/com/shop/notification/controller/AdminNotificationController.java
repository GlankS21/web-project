package com.shop.notification.controller;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.notification.dto.response.NotificationResponse;
import com.shop.notification.entity.NotificationStatus;
import com.shop.notification.entity.NotificationType;
import com.shop.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Success messages are resolved from {@code messages_ru.properties}.
 */
@Tag(name = "Управление уведомлениями", description = "Все письма и повторная отправка (только для администраторов)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/notifications/admin")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final MessageSource messageSource;

    @Operation(summary = "Поиск уведомлений, сначала новые")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> search(
            @Parameter(description = "Фильтр по статусу отправки")
            @RequestParam(name = "status", required = false) NotificationStatus status,
            @Parameter(description = "Фильтр по типу письма")
            @RequestParam(name = "type", required = false) NotificationType type,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(notificationService.search(status, type, pageable));
    }

    @Operation(summary = "Отправить письмо повторно", description = "Используется сохранённый текст письма.")
    @PostMapping("/{notificationId}/resend")
    public ApiResponse<NotificationResponse> resend(@PathVariable("notificationId") UUID notificationId) {
        return ApiResponse.success(messageSource.getMessage("notification.resent", null,
                CommonMessagesAutoConfiguration.DEFAULT_LOCALE), notificationService.resend(notificationId));
    }
}

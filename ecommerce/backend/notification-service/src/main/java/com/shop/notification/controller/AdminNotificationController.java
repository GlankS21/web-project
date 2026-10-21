package com.shop.notification.controller;

import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.notification.dto.response.NotificationResponse;
import com.shop.notification.entity.NotificationStatus;
import com.shop.notification.entity.NotificationType;
import com.shop.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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

@Tag(name = "Notification management", description = "All e-mails and manual resend (admin only)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/notifications/admin")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Search notifications")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> search(
            @RequestParam(name = "status", required = false) NotificationStatus status,
            @RequestParam(name = "type", required = false) NotificationType type,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(notificationService.search(status, type, pageable));
    }

    @Operation(summary = "Send an e-mail again")
    @PostMapping("/{notificationId}/resend")
    public ApiResponse<NotificationResponse> resend(@PathVariable("notificationId") UUID notificationId) {
        return ApiResponse.success("Notification resent", notificationService.resend(notificationId));
    }
}

package com.shop.notification.controller;

import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.security.SecurityUtils;
import com.shop.notification.dto.response.NotificationResponse;
import com.shop.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications", description = "E-mails sent to the authenticated customer")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "My notifications, newest first")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> mine(
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(notificationService.listOf(SecurityUtils.currentUserId(), pageable));
    }
}

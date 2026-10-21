package com.shop.notification.exception;

import com.shop.common.web.AbstractGlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Uses the platform-wide translation of exceptions into {@code ErrorResponse}
 * (validation, business, security and persistence errors). notification-service raises only
 * common exceptions, so no additional handler is required.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
}

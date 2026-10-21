package com.shop.product.exception;

import com.shop.common.web.AbstractGlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Uses the platform-wide translation of exceptions into {@code ErrorResponse}
 * (validation, business, security and persistence errors). product-service raises only
 * common exceptions, so no additional handler is required.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
}

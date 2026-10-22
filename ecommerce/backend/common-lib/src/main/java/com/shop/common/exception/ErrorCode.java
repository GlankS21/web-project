package com.shop.common.exception;

import lombok.Getter;

/**
 * Platform-wide error codes and the HTTP status each one maps to. The English default message is
 * used for logs; responses carry the localized message of {@link #messageKeyOf(ErrorCode)}.
 */
@Getter
public enum ErrorCode {

    VALIDATION_FAILED(400, "Request validation failed"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Authentication is required"),
    FORBIDDEN(403, "Access is denied"),
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    METHOD_NOT_ALLOWED(405, "HTTP method not allowed"),
    CONFLICT(409, "Resource conflict"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    BUSINESS_RULE_VIOLATION(422, "Business rule violation"),
    TOO_MANY_REQUESTS(429, "Too many requests"),
    INTERNAL_ERROR(500, "An unexpected error occurred"),
    SERVICE_UNAVAILABLE(503, "Service temporarily unavailable"),
    GATEWAY_TIMEOUT(504, "Upstream service timed out");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /**
     * Key of the localized default message in {@code common-messages_ru.properties}.
     */
    public static String messageKeyOf(ErrorCode code) {
        return "error." + code.name();
    }
}

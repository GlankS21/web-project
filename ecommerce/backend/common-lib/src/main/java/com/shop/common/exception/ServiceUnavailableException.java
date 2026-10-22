package com.shop.common.exception;

/**
 * A downstream dependency is unreachable or its circuit breaker is open (HTTP 503).
 */
public class ServiceUnavailableException extends BaseException {

    public ServiceUnavailableException(String message, Object... args) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, args);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}

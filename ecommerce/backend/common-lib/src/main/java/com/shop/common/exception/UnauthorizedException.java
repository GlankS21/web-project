package com.shop.common.exception;

/**
 * Authentication is missing, invalid or expired (HTTP 401).
 */
public class UnauthorizedException extends BaseException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED);
    }

    public UnauthorizedException(String message, Object... args) {
        super(ErrorCode.UNAUTHORIZED, message, args);
    }
}

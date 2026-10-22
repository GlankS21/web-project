package com.shop.common.exception;

/**
 * The request is syntactically valid but cannot be processed as sent (HTTP 400).
 */
public class BadRequestException extends BaseException {

    /**
     * @param message key of the message (or literal text) and its arguments
     */
    public BadRequestException(String message, Object... args) {
        super(ErrorCode.BAD_REQUEST, message, args);
    }
}

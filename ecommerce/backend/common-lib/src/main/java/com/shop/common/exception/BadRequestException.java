package com.shop.common.exception;

/**
 * The request is syntactically valid but cannot be processed as sent (HTTP 400).
 */
public class BadRequestException extends BaseException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }
}

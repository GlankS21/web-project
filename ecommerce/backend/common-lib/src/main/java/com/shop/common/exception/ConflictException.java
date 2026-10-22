package com.shop.common.exception;

/**
 * The request conflicts with the current state of a resource, e.g. a duplicate email (HTTP 409).
 */
public class ConflictException extends BaseException {

    /**
     * @param message key of the message (or literal text) and its arguments
     */
    public ConflictException(String message, Object... args) {
        super(ErrorCode.CONFLICT, message, args);
    }
}

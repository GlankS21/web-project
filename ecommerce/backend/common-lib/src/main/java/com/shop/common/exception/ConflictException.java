package com.shop.common.exception;

/**
 * The request conflicts with the current state of a resource, e.g. a duplicate email (HTTP 409).
 */
public class ConflictException extends BaseException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}

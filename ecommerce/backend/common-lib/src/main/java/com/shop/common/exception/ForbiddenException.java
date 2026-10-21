package com.shop.common.exception;

/**
 * The caller is authenticated but not allowed to perform the action (HTTP 403).
 */
public class ForbiddenException extends BaseException {

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}

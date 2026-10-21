package com.shop.common.exception;

import lombok.Getter;

/**
 * Root of all expected (business) exceptions. The global exception handler translates it
 * into an {@link com.shop.common.dto.ErrorResponse} using its {@link ErrorCode}.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    protected BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}

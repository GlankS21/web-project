package com.shop.common.exception;

import lombok.Getter;

/**
 * Root of all expected (business) exceptions. The global exception handler translates it
 * into an {@link com.shop.common.dto.ErrorResponse} using its {@link ErrorCode}.
 * <p>
 * The message is a key of the service's {@code messages_ru.properties} resolved with
 * {@link #getMessageArgs()}; text that is not a known key is returned as it is.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private static final Object[] NO_ARGS = new Object[0];

    private final ErrorCode errorCode;
    private final transient Object[] messageArgs;

    protected BaseException(ErrorCode errorCode) {
        this(errorCode, ErrorCode.messageKeyOf(errorCode), NO_ARGS);
    }

    protected BaseException(ErrorCode errorCode, String message, Object... messageArgs) {
        super(message);
        this.errorCode = errorCode;
        this.messageArgs = messageArgs == null ? NO_ARGS : messageArgs.clone();
    }

    protected BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.messageArgs = NO_ARGS;
    }

    public Object[] getMessageArgs() {
        return messageArgs.clone();
    }
}

package com.shop.common.exception;

/**
 * A domain rule was violated, e.g. cancelling an order that is already shipped (HTTP 422).
 */
public class BusinessException extends BaseException {

    /**
     * @param message key of the message (or literal text) and its arguments
     */
    public BusinessException(String message, Object... args) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message, args);
    }
}

package com.shop.common.exception;

/**
 * A domain rule was violated, e.g. cancelling an order that is already shipped (HTTP 422).
 */
public class BusinessException extends BaseException {

    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}

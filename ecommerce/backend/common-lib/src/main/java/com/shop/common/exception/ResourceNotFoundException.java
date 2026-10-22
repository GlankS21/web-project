package com.shop.common.exception;

/**
 * The requested resource does not exist (HTTP 404).
 */
public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s not found with %s '%s'".formatted(resource, field, value));
    }

    /**
     * @param messageKey key of the message and its arguments
     */
    public static ResourceNotFoundException of(String messageKey, Object... args) {
        return new ResourceNotFoundException(messageKey, args);
    }

    private ResourceNotFoundException(String messageKey, Object[] args) {
        super(ErrorCode.RESOURCE_NOT_FOUND, messageKey, args);
    }
}

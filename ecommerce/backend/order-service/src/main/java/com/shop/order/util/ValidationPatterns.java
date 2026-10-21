package com.shop.order.util;

public final class ValidationPatterns {

    /** Optional leading +, then 9 to 15 digits (E.164 length). */
    public static final String PHONE = "^\\+?[0-9]{9,15}$";
    public static final String PHONE_MESSAGE = "must be a phone number of 9 to 15 digits, optionally starting with +";

    /** ISO 3166-1 alpha-2 country code. */
    public static final String COUNTRY_CODE = "^[A-Z]{2}$";
    public static final String COUNTRY_CODE_MESSAGE = "must be an ISO 3166-1 alpha-2 code such as VN";

    private ValidationPatterns() {
    }
}

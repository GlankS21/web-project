package com.shop.order.util;

public final class ValidationPatterns {

    /** Russian mobile or landline number: +7 followed by 10 digits. */
    public static final String PHONE = "^\\+7\\d{10}$";
    /** Resolved from common-lib's {@code common-messages_ru.properties}. */
    public static final String PHONE_MESSAGE = "{validation.phone}";

    /** ISO 3166-1 alpha-2 country code. */
    public static final String COUNTRY_CODE = "^[A-Z]{2}$";
    /** Resolved from common-lib's {@code common-messages_ru.properties}. */
    public static final String COUNTRY_CODE_MESSAGE = "{validation.country-code}";

    private ValidationPatterns() {
    }
}

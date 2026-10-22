package com.shop.user.util;

/**
 * Bean Validation patterns of user-service. The messages are keys of the shared
 * {@code common-messages_ru.properties}.
 */
public final class ValidationPatterns {

    /** Russian mobile or landline number in international format: +7 then 10 digits. */
    public static final String PHONE = "^\\+7\\d{10}$";
    public static final String PHONE_MESSAGE = "{validation.phone}";

    /** ISO 3166-1 alpha-2 country code. */
    public static final String COUNTRY_CODE = "^[A-Z]{2}$";
    public static final String COUNTRY_CODE_MESSAGE = "{validation.country-code}";

    private ValidationPatterns() {
    }
}

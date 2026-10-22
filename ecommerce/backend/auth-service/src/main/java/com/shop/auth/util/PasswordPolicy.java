package com.shop.auth.util;

/**
 * Password rules shared by registration and password change.
 * The 72 character limit comes from bcrypt, which ignores any input beyond 72 bytes.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    /** Key of the Bean Validation message in {@code messages_ru.properties}. */
    public static final String PATTERN_MESSAGE = "{auth.validation.password-pattern}";

    private PasswordPolicy() {
    }
}

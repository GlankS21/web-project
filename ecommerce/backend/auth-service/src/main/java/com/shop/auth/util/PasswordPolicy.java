package com.shop.auth.util;

/**
 * Password rules shared by registration and password change.
 * The 72 character limit comes from bcrypt, which ignores any input beyond 72 bytes.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String PATTERN_MESSAGE = "Password must contain at least one letter and one digit";

    private PasswordPolicy() {
    }
}

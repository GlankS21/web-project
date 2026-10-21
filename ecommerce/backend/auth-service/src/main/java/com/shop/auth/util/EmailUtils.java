package com.shop.auth.util;

import java.util.Locale;

public final class EmailUtils {

    private EmailUtils() {
    }

    /**
     * Emails are compared case-insensitively; they are always stored trimmed and lower-cased.
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

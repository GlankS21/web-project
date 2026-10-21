package com.shop.product.util;

public final class ValidationPatterns {

    /** Upper-case letters, digits and dashes, 3 to 64 characters, e.g. PHN-IP15-128. */
    public static final String SKU = "^[A-Z0-9][A-Z0-9-]{2,63}$";
    public static final String SKU_MESSAGE = "must be 3 to 64 upper-case letters, digits or dashes";

    /** Lower-case ASCII words separated by single dashes, e.g. iphone-15-128gb. */
    public static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    public static final String SLUG_MESSAGE = "must be lower-case letters and digits separated by dashes";

    public static final String HTTP_URL = "^https?://\\S+$";
    public static final String HTTP_URL_MESSAGE = "must be an http or https URL";

    private ValidationPatterns() {
    }
}

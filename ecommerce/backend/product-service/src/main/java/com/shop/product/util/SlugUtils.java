package com.shop.product.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds URL slugs from display names, including Vietnamese ones ("Điện thoại" → "dien-thoai").
 */
public final class SlugUtils {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+|-+$)");

    private SlugUtils() {
    }

    /**
     * @return the slug, at most {@code maxLength} characters, or an empty string when the text
     * contains no letter or digit
     */
    public static String slugify(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        // "đ" is a distinct letter, not "d" plus a combining mark, so NFD does not decompose it
        String ascii = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        ascii = COMBINING_MARKS.matcher(ascii).replaceAll("");
        String slug = NON_ALPHANUMERIC.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = EDGE_DASHES.matcher(slug).replaceAll("");
        if (slug.length() > maxLength) {
            slug = EDGE_DASHES.matcher(slug.substring(0, maxLength)).replaceAll("");
        }
        return slug;
    }
}

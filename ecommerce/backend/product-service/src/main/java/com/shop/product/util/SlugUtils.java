package com.shop.product.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds URL slugs from display names: lower-case ASCII letters and digits separated by single dashes,
 * e.g. "Ноутбуки и ультрабуки" → "noutbuki-i-ultrabuki", "iPhone 15 (128GB)" → "iphone-15-128gb".
 * <p>
 * Cyrillic is transliterated letter by letter with the passport-style table of ICAO Doc 9303
 * (the one used for names in Russian international passports):
 * <pre>
 * а a   б b   в v   г g   д d   е e   ё e   ж zh  з z   и i   й i
 * к k   л l   м m   н n   о o   п p   р r   с s   т t   у u   ф f
 * х kh  ц ts  ч ch  ш sh  щ shch  ъ ie  ы y   ь (dropped)   э e   ю iu  я ia
 * </pre>
 * Latin letters with diacritics lose their marks (an acute e becomes "e"); the few Latin letters that
 * do not decompose into a base letter and marks (sharp s, ligatures, letters with a stroke...) have
 * their own replacement. Every other character is a separator.
 */
public final class SlugUtils {

    /** Transliteration of the lower-case letters а (U+0430) to я (U+044F), in alphabetical order. */
    private static final String[] CYRILLIC = {
            "a", "b", "v", "g", "d", "e", "zh", "z", "i", "i", "k", "l", "m", "n", "o", "p",
            "r", "s", "t", "u", "f", "kh", "ts", "ch", "sh", "shch", "ie", "y", "", "e", "iu", "ia"
    };
    private static final char CYRILLIC_FIRST = '\u0430';
    private static final char CYRILLIC_LAST = '\u044F';
    private static final char CYRILLIC_YO = '\u0451';

    /** Latin letters that Unicode normalisation does not reduce to an ASCII base letter. */
    private static final Map<Character, String> LATIN_SPECIAL = Map.of(
            '\u00DF', "ss",  // sharp s
            '\u00E6', "ae",  // ae ligature
            '\u0153', "oe",  // oe ligature
            '\u00F8', "o",   // o with stroke
            '\u0142', "l",   // l with stroke
            '\u0111', "d",   // d with stroke
            '\u00F0', "d",   // eth
            '\u00FE', "th",  // thorn
            '\u0131', "i"    // dotless i
    );

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
        // Transliterate before normalising: NFD would split "й" and "ё" into a base letter and a mark
        String latin = transliterate(text.toLowerCase(Locale.ROOT));
        String ascii = COMBINING_MARKS.matcher(Normalizer.normalize(latin, Normalizer.Form.NFD)).replaceAll("");
        String slug = NON_ALPHANUMERIC.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = EDGE_DASHES.matcher(slug).replaceAll("");
        if (slug.length() > maxLength) {
            slug = EDGE_DASHES.matcher(slug.substring(0, maxLength)).replaceAll("");
        }
        return slug;
    }

    private static String transliterate(String lowerCase) {
        StringBuilder result = new StringBuilder(lowerCase.length() + 16);
        for (int i = 0; i < lowerCase.length(); i++) {
            char c = lowerCase.charAt(i);
            if (c >= CYRILLIC_FIRST && c <= CYRILLIC_LAST) {
                result.append(CYRILLIC[c - CYRILLIC_FIRST]);
            } else if (c == CYRILLIC_YO) {
                result.append('e');
            } else {
                String replacement = LATIN_SPECIAL.get(c);
                if (replacement != null) {
                    result.append(replacement);
                } else {
                    result.append(c);
                }
            }
        }
        return result.toString();
    }
}

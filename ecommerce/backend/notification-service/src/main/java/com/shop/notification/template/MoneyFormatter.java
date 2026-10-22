package com.shop.notification.template;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats amounts the Russian way: {@code 1 234,50 ₽}, with non-breaking spaces between the groups of
 * digits and before the currency. Roubles are shown with the ruble sign, other currencies keep their
 * ISO code ({@code 19,99 USD}).
 */
public final class MoneyFormatter {

    static final char NBSP = ' ';
    private static final String RUB = "RUB";
    private static final String RUBLE_SIGN = "₽";

    private MoneyFormatter() {
    }

    public static String format(BigDecimal amount, String currency) {
        if (amount == null) {
            return "—";
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU"));
        symbols.setGroupingSeparator(NBSP);
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('-');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        String value = format.format(amount.setScale(2, RoundingMode.HALF_UP));
        String unit = currency == null || RUB.equalsIgnoreCase(currency) ? RUBLE_SIGN : currency;
        return value + NBSP + unit;
    }
}

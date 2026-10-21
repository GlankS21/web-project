package com.shop.notification.template;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Set;

/**
 * Formats amounts the Vietnamese way: {@code 1.234.567 VND}, {@code 19,99 USD}. Currencies without
 * minor unit (VND, JPY, KRW) are shown without decimals.
 */
public final class MoneyFormatter {

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of("VND", "JPY", "KRW");
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN"));

    private MoneyFormatter() {
    }

    public static String format(BigDecimal amount, String currency) {
        if (amount == null) {
            return "-";
        }
        boolean noDecimals = currency == null || ZERO_DECIMAL_CURRENCIES.contains(currency);
        DecimalFormat format = new DecimalFormat(noDecimals ? "#,##0" : "#,##0.00", SYMBOLS);
        String value = format.format(amount.setScale(noDecimals ? 0 : 2, RoundingMode.HALF_UP));
        return currency == null ? value : value + " " + currency;
    }
}

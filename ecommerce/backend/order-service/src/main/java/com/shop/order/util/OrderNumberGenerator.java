package com.shop.order.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Human-friendly order numbers such as {@code ORD-20261021-K7QX2M}: the UTC date plus 6 random
 * characters (about 10^9 combinations per day). Ambiguous characters (0/O, 1/I/L) are left out so
 * the number can be read over the phone. Uniqueness is enforced by the database.
 */
@Component
public class OrderNumberGenerator {

    private static final String PREFIX = "ORD-";
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int RANDOM_LENGTH = 6;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public OrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder number = new StringBuilder(PREFIX)
                .append(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).format(DATE))
                .append('-');
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            number.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return number.toString();
    }
}

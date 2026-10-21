package com.shop.common.util;

import com.shop.common.constant.LoggingConstants;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Helpers to read, validate and bind the correlation id to the logging context.
 */
public final class CorrelationIdUtils {

    private static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._-]+$");

    private CorrelationIdUtils() {
    }

    /**
     * Returns the incoming id when it is safe to log, otherwise a freshly generated one.
     * Rejecting arbitrary values prevents log forging through a crafted header.
     */
    public static String resolveOrGenerate(String candidate) {
        if (candidate != null
                && !candidate.isBlank()
                && candidate.length() <= MAX_LENGTH
                && SAFE_VALUE.matcher(candidate).matches()) {
            return candidate;
        }
        return generate();
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static String current() {
        return MDC.get(LoggingConstants.MDC_CORRELATION_ID);
    }

    public static String currentOrGenerate() {
        String current = current();
        return current != null ? current : generate();
    }

    public static void bind(String correlationId) {
        MDC.put(LoggingConstants.MDC_CORRELATION_ID, correlationId);
    }

    public static void clear() {
        MDC.remove(LoggingConstants.MDC_CORRELATION_ID);
    }
}

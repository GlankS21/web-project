package ru.itmo.vkr.channels;

import java.util.regex.Pattern;

/**
 * Matching rules of the three naming schemes that connect services: Spring MVC path templates,
 * AMQP topic routing keys and the Ant-style path predicates of Spring Cloud Gateway.
 */
final class Patterns {

    private Patterns() {
    }

    /**
     * Joins path fragments into a normalised path: one leading slash, no duplicate or trailing slash.
     */
    static String joinPaths(String... parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.startsWith("/")) {
                joined.append('/');
            }
            joined.append(trimmed);
        }
        String path = joined.toString().replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    /**
     * Whether two path templates can denote the same request path. A {@code {variable}} segment on
     * either side matches any single segment.
     */
    static boolean pathTemplatesMatch(String left, String right) {
        String[] a = segments(left);
        String[] b = segments(right);
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!isVariable(a[i]) && !isVariable(b[i]) && !a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Number of positions where one template has a {@code {variable}} and the other a literal
     * segment; lower means a closer match. Among endpoints that can serve a call, the closest are
     * the ones Spring MVC routes it to in practice: {@code /items/featured} goes to the literal
     * handler, {@code /items/{id}} to the variable one.
     */
    static int segmentKindMismatches(String left, String right) {
        String[] a = segments(left);
        String[] b = segments(right);
        int count = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (isVariable(a[i]) != isVariable(b[i])) {
                count++;
            }
        }
        return count;
    }

    /**
     * AMQP topic matching: {@code *} is exactly one word, {@code #} zero or more words.
     */
    static boolean topicMatches(String bindingKey, String routingKey) {
        return topicMatches(words(bindingKey), 0, words(routingKey), 0);
    }

    /**
     * Compiles a gateway {@code Path=} predicate. A trailing {@code /**} also matches the prefix
     * itself, as Spring's {@code PathPattern} does.
     */
    static Pattern antPattern(String pattern) {
        String p = pattern.trim();
        boolean trailingWildcard = p.endsWith("/**");
        if (trailingWildcard) {
            p = p.substring(0, p.length() - 3);
        }
        StringBuilder regex = new StringBuilder("^");
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < p.length()) {
            char c = p.charAt(i);
            String token;
            if (p.startsWith("**", i)) {
                token = ".*";
                i += 2;
            } else if (c == '*') {
                token = "[^/]*";
                i++;
            } else if (c == '?') {
                token = "[^/]";
                i++;
            } else if (c == '{' && p.indexOf('}', i) > i) {
                token = "[^/]+";
                i = p.indexOf('}', i) + 1;
            } else {
                literal.append(c);
                i++;
                continue;
            }
            flushLiteral(regex, literal);
            regex.append(token);
        }
        flushLiteral(regex, literal);
        if (trailingWildcard) {
            regex.append("(/.*)?");
        }
        return Pattern.compile(regex.append('$').toString());
    }

    private static void flushLiteral(StringBuilder regex, StringBuilder literal) {
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
            literal.setLength(0);
        }
    }

    private static boolean topicMatches(String[] pattern, int i, String[] words, int j) {
        if (i == pattern.length) {
            return j == words.length;
        }
        if (pattern[i].equals("#")) {
            for (int k = j; k <= words.length; k++) {
                if (topicMatches(pattern, i + 1, words, k)) {
                    return true;
                }
            }
            return false;
        }
        if (j == words.length) {
            return false;
        }
        return (pattern[i].equals("*") || pattern[i].equals(words[j])) && topicMatches(pattern, i + 1, words, j + 1);
    }

    private static String[] words(String key) {
        return key.isEmpty() ? new String[0] : key.split("\\.", -1);
    }

    private static String[] segments(String path) {
        String trimmed = joinPaths(path).substring(1);
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    private static boolean isVariable(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }
}

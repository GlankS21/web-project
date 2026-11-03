package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.language.annotation.Annotation;
import pascal.taie.language.annotation.StringElement;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Bean Validation as a sanitizer: a field annotated with {@code @Pattern} whose regular expression
 * admits neither quotes nor line breaks (e.g. coupon codes {@code ^[A-Za-z0-9_-]{3,30}$}) holds no
 * text that could inject SQL or forge a log line, so taint is dropped where the field is read.
 *
 * <p>The request DTOs are validated when Spring binds the body ({@code @Valid}); values a service
 * puts into such a DTO itself are not, which this barrier assumes does not happen. A pattern the
 * check below does not understand is treated as unsafe and keeps the taint.
 */
public class ValidatedFieldBarrier implements Plugin {

    private static final String[] PATTERN = {"jakarta.validation.constraints.Pattern",
            "javax.validation.constraints.Pattern"};

    /** quotes, backslash, statement separator, line breaks */
    private static final String DANGEROUS = "'\"`\\;\r\n\u0085  ";

    private static final Predicate<CSObj> NOT_TAINT = o -> !ContainerTaint.isTaint(o);

    private Solver solver;

    private CSManager csManager;

    private final Map<JField, Boolean> validated = new HashMap<>();

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.csManager = solver.getCSManager();
    }

    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        JMethod method = csMethod.getMethod();
        if (method.isAbstract() || method.isNative() || !method.getDeclaringClass().isApplication()) {
            return;
        }
        Context ctx = csMethod.getContext();
        for (var stmt : method.getIR()) {
            if (stmt instanceof LoadField load) {
                JField field = load.getFieldAccess().getFieldRef().resolveNullable();
                if (field != null && validated.computeIfAbsent(field, ValidatedFieldBarrier::hasSafePattern)) {
                    solver.addPointerFilter(csManager.getCSVar(ctx, load.getLValue()), NOT_TAINT);
                }
            }
        }
    }

    private static boolean hasSafePattern(JField field) {
        for (String type : PATTERN) {
            Annotation pattern = field.getAnnotation(type);
            if (pattern != null && pattern.getElement("regexp") instanceof StringElement regexp) {
                return admitsNoDangerousChar(regexp.value());
            }
        }
        return false;
    }

    /**
     * Conservative check of a regular expression: literals, escaped punctuation, \d, \w, positive
     * character classes, groups, alternation, anchors and quantifiers are understood; '.', negated
     * classes, \s, \W, \p{..}, lookarounds and anything else make the pattern unsafe.
     */
    static boolean admitsNoDangerousChar(String regex) {
        int i = 0;
        int n = regex.length();
        while (i < n) {
            char c = regex.charAt(i);
            switch (c) {
                case '^', '$', ')', '|', '*', '+', '?' -> i++;
                case '(' -> {
                    if (i + 1 < n && regex.charAt(i + 1) == '?') {
                        if (!regex.startsWith("(?:", i)) {
                            return false;
                        }
                        i += 3;
                    } else {
                        i++;
                    }
                }
                case '{' -> {
                    int end = regex.indexOf('}', i);
                    if (end < 0 || !regex.substring(i + 1, end).matches("\\d+(,\\d*)?")) {
                        return false;
                    }
                    i = end + 1;
                }
                case '[' -> {
                    int end = safeClassEnd(regex, i + 1);
                    if (end < 0) {
                        return false;
                    }
                    i = end + 1;
                }
                case '\\' -> {
                    if (i + 1 >= n || !safeEscape(regex.charAt(i + 1))) {
                        return false;
                    }
                    i += 2;
                }
                case '.' -> {
                    return false;
                }
                default -> {
                    if (DANGEROUS.indexOf(c) >= 0) {
                        return false;
                    }
                    i++;
                }
            }
        }
        return true;
    }

    /** index of the ']' closing a class that starts at {@code i}, or -1 if the class is unsafe */
    private static int safeClassEnd(String regex, int i) {
        int n = regex.length();
        if (i < n && regex.charAt(i) == '^') {
            return -1;
        }
        boolean first = true;
        while (i < n) {
            char c = regex.charAt(i);
            if (c == ']' && !first) {
                return i;
            }
            first = false;
            if (c == '[' || regex.startsWith("&&", i)) {
                return -1;
            }
            if (c == '\\') {
                if (i + 1 >= n || !safeEscape(regex.charAt(i + 1))) {
                    return -1;
                }
                i += 2;
                continue;
            }
            if (i + 2 < n && regex.charAt(i + 1) == '-' && regex.charAt(i + 2) != ']') {
                char to = regex.charAt(i + 2);
                for (char x = c; x <= to; x++) {
                    if (DANGEROUS.indexOf(x) >= 0) {
                        return -1;
                    }
                }
                i += 3;
                continue;
            }
            if (DANGEROUS.indexOf(c) >= 0) {
                return -1;
            }
            i++;
        }
        return -1;
    }

    /** \d and \w, or an escaped punctuation character that is itself harmless */
    private static boolean safeEscape(char e) {
        return e == 'd' || e == 'w' || !Character.isLetterOrDigit(e) && DANGEROUS.indexOf(e) < 0;
    }
}

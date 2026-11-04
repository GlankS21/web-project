package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import micans.plugin.taint.ParamFieldSourcePoint;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.plugin.taint.ParamSourcePoint;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InvokeDynamic;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.generics.MethodGSignature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Taint through string concatenation with {@code +}.
 *
 * <p>Since Java 9, javac compiles {@code "a" + x} to an {@code invokedynamic} of
 * {@code StringConcatFactory.makeConcatWithConstants}, which Tai-e does not model: the result
 * variable points to nothing, so {@code "... where id = '" + input + "'"} lost the taint of
 * {@code input} before reaching the SQL sink. Here every taint object of an operand also flows to
 * the result, keeping its source point. Application code only, like {@link ContainerTaint}.
 *
 * <p>A taint whose source is declared numeric ({@code Long id}, {@code List<Long> ids}, a DTO field
 * of such a type) does not pass: rendered into text it is digits, separators and brackets, which
 * cannot close a quote or start a new statement or log line.
 */
public class StringConcatTaint implements Plugin {

    private static final String FACTORY = "java.lang.invoke.StringConcatFactory";

    private static final Set<String> NUMBERS = Set.of("byte", "short", "int", "long", "float", "double",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float",
            "java.lang.Double", "java.math.BigInteger", "java.math.BigDecimal");

    private static final Set<String> NUMBER_CONTAINERS = Set.of("java.util.Collection", "java.util.List",
            "java.util.Set", "java.util.Optional");

    private static final Pattern TYPE_NAME = Pattern.compile("[A-Za-z_$][\\w.$]*");

    private Solver solver;

    private CSManager csManager;

    /** operand -> results of the concatenations it takes part in */
    private final Map<CSVar, List<CSVar>> operandToResults = new HashMap<>();

    /** taint object -> whether its source is declared numeric */
    private final Map<CSObj, Boolean> numeric = new HashMap<>();

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
        method.getIR().invokes(true).forEach(invoke -> register(ctx, invoke));
    }

    private void register(Context ctx, Invoke invoke) {
        if (!(invoke.getInvokeExp() instanceof InvokeDynamic indy)
                || !FACTORY.equals(indy.getBootstrapMethodRef().getDeclaringClass().getName())
                || invoke.getResult() == null) {
            return;
        }
        CSVar result = csManager.getCSVar(ctx, invoke.getResult());
        for (Var arg : indy.getArgs()) {
            CSVar operand = csManager.getCSVar(ctx, arg);
            operandToResults.computeIfAbsent(operand, k -> new ArrayList<>()).add(result);
            propagate(solver.getPointsToSetOf(operand), result);
        }
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        for (CSVar result : operandToResults.getOrDefault(csVar, List.of())) {
            propagate(pts, result);
        }
    }

    private void propagate(PointsToSet operand, CSVar result) {
        if (operand == null) {
            return;
        }
        PointsToSet taints = solver.makePointsToSet();
        for (CSObj obj : operand) {
            if (ContainerTaint.isTaint(obj) && !numeric.computeIfAbsent(obj, StringConcatTaint::hasNumericSource)) {
                taints.addObject(obj);
            }
        }
        if (!taints.isEmpty()) {
            solver.addPointsTo(result, taints);
        }
    }

    /** The declared type of the taint's source: a method parameter or a field of one. */
    private static boolean hasNumericSource(CSObj taint) {
        Object source = taint.getObject() instanceof MockObj mock ? mock.getAllocation() : null;
        if (source instanceof ParamSourcePoint p && p.indexRef().index() >= 0 && p.indexRef().field() == null) {
            JMethod m = p.sourceMethod();
            int i = p.indexRef().index();
            MethodGSignature sig = m.getGSignature();
            return isNumeric(sig != null && i < sig.getParameterSigs().size()
                    ? sig.getParameterSigs().get(i).toString() : m.getParamType(i).getName());
        }
        if (source instanceof ParamFieldSourcePoint p) {
            JField f = p.instanceField().getField();
            return isNumeric(f.getGSignature() != null ? f.getGSignature().toString() : f.getType().getName());
        }
        return false;
    }

    /** Every type named in the signature is a number or a container of numbers, and one is a number. */
    private static boolean isNumeric(String signature) {
        List<String> names = TYPE_NAME.matcher(signature).results().map(MatchResult::group).toList();
        return names.stream().anyMatch(NUMBERS::contains)
                && names.stream().allMatch(n -> NUMBERS.contains(n) || NUMBER_CONTAINERS.contains(n));
    }
}

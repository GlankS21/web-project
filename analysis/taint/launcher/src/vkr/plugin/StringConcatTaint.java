package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InvokeDynamic;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Taint through string concatenation with {@code +}.
 *
 * <p>Since Java 9, javac compiles {@code "a" + x} to an {@code invokedynamic} of
 * {@code StringConcatFactory.makeConcatWithConstants}, which Tai-e does not model: the result
 * variable points to nothing, so {@code "... where id = '" + input + "'"} lost the taint of
 * {@code input} before reaching the SQL sink. Here every taint object of an operand also flows to
 * the result, keeping its source point. Application code only, like {@link ContainerTaint}.
 */
public class StringConcatTaint implements Plugin {

    private static final String FACTORY = "java.lang.invoke.StringConcatFactory";

    private Solver solver;

    private CSManager csManager;

    /** operand -> results of the concatenations it takes part in */
    private final Map<CSVar, List<CSVar>> operandToResults = new HashMap<>();

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
            if (ContainerTaint.isTaint(obj)) {
                taints.addObject(obj);
            }
        }
        if (!taints.isEmpty()) {
            solver.addPointsTo(result, taints);
        }
    }
}

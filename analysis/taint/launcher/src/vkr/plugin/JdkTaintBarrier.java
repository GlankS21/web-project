package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.JMethod;

import java.util.List;
import java.util.function.Predicate;

/**
 * Keeps taint objects out of the JDK collection, stream and number code ({@code java.util.*},
 * {@code java.math.*}).
 *
 * <p>Collection internals (HashMap.table and its nodes, ArrayList.elementData, stream sinks, the
 * maps built by Collectors) are allocated inside the JDK without heap context, so under 1-call every
 * collection of the service shares them: an element added anywhere comes out of every iterator
 * (observed: HashSet.iterator().next() in OrderServiceImpl.singleCurrency held 248 taints from
 * unrelated requests). Taint therefore never enters these methods; at the application call site it
 * follows the YAML transfers (iterator, next, get, stream, map, collect, ...) and
 * {@link ContainerTaint}, both in the caller's context. Real objects are not filtered.
 */
public class JdkTaintBarrier implements Plugin {

    /** java.math: BigDecimal arithmetic returns `this` or shared constants through internal helpers */
    private static final List<String> BEHIND_BARRIER = List.of("java.util.", "java.math.");

    private static final Predicate<CSObj> NOT_TAINT = o -> !ContainerTaint.isTaint(o);

    private Solver solver;

    private CSManager csManager;

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.csManager = solver.getCSManager();
    }

    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        JMethod method = csMethod.getMethod();
        String cls = method.getDeclaringClass().getName();
        if (BEHIND_BARRIER.stream().noneMatch(cls::startsWith)
                || method.isAbstract() || method.isNative()) {
            return;
        }
        Context ctx = csMethod.getContext();
        IR ir = method.getIR();
        if (ir.getThis() != null) {
            solver.addPointerFilter(csManager.getCSVar(ctx, ir.getThis()), NOT_TAINT);
        }
        for (Var param : ir.getParams()) {
            solver.addPointerFilter(csManager.getCSVar(ctx, param), NOT_TAINT);
        }
    }
}

package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A variable of an application method declared as a number, boolean, UUID, date/time or enum holds
 * no taint: such a value cannot contain a quote or a line break, whatever the client sent.
 *
 * <p>The sources and channels are declared per parameter, record component and entity field, ids
 * and amounts included (an event's eventId, Order.totalAmount, a {productId} path variable), and
 * logging those values was reported as log injection. Dropping taint at every variable of such a
 * type also stops it after a conversion (UUID.toString of a tainted id reads a clean base). The
 * value's own text form is fixed by its type; Character is kept, being a single arbitrary char.
 */
public class NonTextVarBarrier implements Plugin {

    private static final Set<String> NON_TEXT = Set.of("java.lang.Byte", "java.lang.Short",
            "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Boolean",
            "java.math.BigInteger", "java.math.BigDecimal", "java.util.UUID");

    private static final Predicate<CSObj> NOT_TAINT = o -> !ContainerTaint.isTaint(o);

    private Solver solver;

    private CSManager csManager;

    private final Map<Type, Boolean> nonText = new HashMap<>();

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
        for (Var var : method.getIR().getVars()) {
            if (nonText.computeIfAbsent(var.getType(), NonTextVarBarrier::isNonText)) {
                solver.addPointerFilter(csManager.getCSVar(ctx, var), NOT_TAINT);
            }
        }
    }

    private static boolean isNonText(Type type) {
        if (!(type instanceof ClassType cls)) {
            return false; // primitives hold no objects; arrays and the null type are left alone
        }
        String name = cls.getName();
        return NON_TEXT.contains(name) || name.startsWith("java.time.")
                || cls.getJClass() != null && cls.getJClass().isEnum();
    }
}

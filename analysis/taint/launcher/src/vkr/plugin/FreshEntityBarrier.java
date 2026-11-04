package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.NewObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An entity field read is the receiving end of a database channel only when the entity may come
 * from the database: a read of an entity the request itself created ({@code new}) returns what the
 * request put there, which the heap already tracks.
 *
 * <p>Tai-e taints the target of a field-source load as soon as the method is reached, before the
 * base is known, so that taint is held back here: it is let through when the base points to a row
 * (or any object not allocated by the application), and at the end of a phase when the base points
 * to nothing (child entities of a row, whose fields the row mock does not fill). It stays dropped
 * while every object of the base is allocated by the application. Observed: inventory's
 * {@code reserve} reads {@code failureReason} back from the reservation it has just created and
 * filled with its own message, and the field source reported every cancel reason ever stored there
 * as part of the stock.reservation-failed reply.
 */
public class FreshEntityBarrier implements Plugin {

    private static final List<String> PERSISTENT = List.of("jakarta.persistence.Entity",
            "jakarta.persistence.Embeddable", "jakarta.persistence.MappedSuperclass");

    private Solver solver;

    private CSManager csManager;

    private Method loadFieldOf;

    private final Map<JClass, Boolean> persistent = new HashMap<>();

    /** base of an entity-field load -> the field-source taints held back at its targets */
    private final Map<CSVar, List<Held>> held = new HashMap<>();

    /** bases whose held taints have been let through */
    private final Set<CSVar> released = new HashSet<>();

    private record Held(CSVar target, CSObj taint) {
    }

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.csManager = solver.getCSManager();
        try {
            // FieldSourcePoint is package-private in Tai-e
            loadFieldOf = Class.forName("pascal.taie.analysis.pta.plugin.taint.FieldSourcePoint")
                    .getDeclaredMethod("loadField");
            loadFieldOf.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        JMethod method = csMethod.getMethod();
        if (method.isAbstract() || method.isNative() || !method.getDeclaringClass().isApplication()) {
            return;
        }
        Context ctx = csMethod.getContext();
        for (var stmt : method.getIR()) {
            if (stmt instanceof LoadField load && load.getFieldAccess() instanceof InstanceFieldAccess access) {
                JField field = load.getFieldRef().resolveNullable();
                if (field == null || !persistent.computeIfAbsent(field.getDeclaringClass(),
                        c -> PERSISTENT.stream().anyMatch(c::hasAnnotation))) {
                    continue;
                }
                CSVar base = csManager.getCSVar(ctx, access.getBase());
                CSVar target = csManager.getCSVar(ctx, load.getLValue());
                solver.addPointerFilter(target, o -> !isSourceOf(o, load) || admits(base, target, o));
            }
        }
    }

    /** lets the taint through if the base may be a row, else holds it back */
    private boolean admits(CSVar base, CSVar target, CSObj taint) {
        if (released.contains(base)) {
            return true;
        }
        if (solver.getPointsToSetOf(base).objects().anyMatch(o -> !isFresh(o))) {
            released.add(base);
            return true;
        }
        held.computeIfAbsent(base, b -> new ArrayList<>()).add(new Held(target, taint));
        return false;
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        if (held.containsKey(csVar) && pts.objects().anyMatch(o -> !isFresh(o))) {
            release(csVar);
        }
    }

    @Override
    public void onPhaseFinish() {
        for (CSVar base : new ArrayList<>(held.keySet())) {
            if (solver.getPointsToSetOf(base).isEmpty()) {
                release(base);
            }
        }
    }

    private void release(CSVar base) {
        released.add(base);
        for (Held h : held.remove(base)) {
            solver.addPointsTo(h.target(), h.taint());
        }
    }

    private boolean isSourceOf(CSObj obj, LoadField load) {
        Object source = obj.getObject().getAllocation();
        if (!ContainerTaint.isTaint(obj) || !source.getClass().getSimpleName().equals("FieldSourcePoint")) {
            return false;
        }
        try {
            return loadFieldOf.invoke(source) == load;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** an object allocated by the application itself */
    private static boolean isFresh(CSObj obj) {
        Obj o = obj.getObject();
        return o instanceof NewObj n && n.getContainerMethod().map(m -> m.getDeclaringClass().isApplication()).orElse(false);
    }
}

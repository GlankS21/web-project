package vkr.plugin;

import pascal.taie.World;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JField;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Taint is not tracked through exception objects: no field of a {@code Throwable} (detailMessage,
 * and the fields subclasses build their message from) holds taint.
 *
 * <p>Tai-e merges the exception objects of one type, and framework code builds messages in shared
 * helpers, so these fields became program-wide unions: in inventory with seeded SQL injection V08,
 * 54 exception types caught by OutboxPublisher.publishPending held the same six taints in
 * detailMessage, charsetName, input, typeName, ... (the SQL of a request thread, the outbox row's
 * event type and id), and every seeded injection was also reported as log injection there. Turning
 * the merging off made the run 3.5 times slower and left the messages tainted. Lost by this
 * barrier: a request's own input echoed in an exception message and logged (CWE-117 through
 * {@code ex.getMessage()}).
 */
public class ExceptionMessageBarrier implements Plugin {

    private static final Predicate<CSObj> NOT_TAINT = o -> !ContainerTaint.isTaint(o);

    private Solver solver;

    private TypeSystem types;

    private Type throwable;

    private final Map<Type, Boolean> exceptionTypes = new HashMap<>();

    private final Map<JClass, List<JField>> fields = new HashMap<>();

    private final Set<CSObj> filtered = new HashSet<>();

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.types = World.get().getTypeSystem();
        this.throwable = types.getType("java.lang.Throwable");
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        for (CSObj obj : pts) {
            if (exceptionTypes.computeIfAbsent(obj.getObject().getType(), t -> types.isSubtype(throwable, t))
                    && !ContainerTaint.isTaint(obj) && filtered.add(obj)
                    && obj.getObject().getType() instanceof ClassType type) {
                for (JField field : fields.computeIfAbsent(type.getJClass(), ExceptionMessageBarrier::instanceFields)) {
                    solver.addPointerFilter(solver.getCSManager().getInstanceField(obj, field), NOT_TAINT);
                }
            }
        }
    }

    private static List<JField> instanceFields(JClass cls) {
        List<JField> out = new ArrayList<>();
        for (JClass c = cls; c != null; c = c.getSuperClass()) {
            c.getDeclaredFields().stream().filter(f -> !f.isStatic()).forEach(out::add);
        }
        return out;
    }
}

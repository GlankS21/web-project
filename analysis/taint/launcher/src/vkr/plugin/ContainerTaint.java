package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Object-level taint for JDK containers.
 *
 * <p>A Tai-e transfer {@code from: 0, to: base} taints the base <em>variable</em> only, so an
 * alias of the same collection in another method never sees it, e.g. a map filled inside a
 * {@code forEach} lambda and read by the enclosing method. Here a write ({@code add}, {@code put},
 * {@code merge}, ...) attaches the taint to every container object the base points to, and a read
 * ({@code iterator}, {@code stream}, {@code keySet}, {@code get}, ...) on any alias of that object
 * returns it.
 */
public class ContainerTaint implements Plugin {

    /** method name -> indexes of the arguments stored into the container */
    private static final Map<String, int[]> COLLECTION_WRITES = Map.of(
            "add", new int[]{-1}, // last argument: add(e) or add(i, e)
            "addLast", new int[]{0}, "addFirst", new int[]{0},
            "set", new int[]{1}, "offer", new int[]{0}, "push", new int[]{0});

    private static final Map<String, int[]> MAP_WRITES = Map.of(
            "put", new int[]{0, 1}, "putIfAbsent", new int[]{0, 1}, "merge", new int[]{0, 1});

    private static final Set<String> COLLECTION_READS = Set.of(
            "iterator", "listIterator", "stream", "parallelStream", "get", "getFirst", "getLast",
            "peek", "poll", "pop", "element", "toArray");

    private static final Set<String> MAP_READS = Set.of(
            "keySet", "values", "entrySet", "get", "getOrDefault", "sequencedKeySet", "sequencedValues");

    private Solver solver;

    private CSManager csManager;

    private ClassHierarchy hierarchy;

    private JClass collectionClass;

    private JClass mapClass;

    /** taint objects attached to each container object */
    private final Map<CSObj, Set<CSObj>> contents = new HashMap<>();

    /** result variables of reads on each container object */
    private final Map<CSObj, Set<CSVar>> readers = new HashMap<>();

    private final Map<CSVar, List<CSVar>> writeArgToBase = new HashMap<>();

    private final Map<CSVar, List<CSVar>> writeBaseToArg = new HashMap<>();

    private final Map<CSVar, List<CSVar>> readBaseToResult = new HashMap<>();

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.csManager = solver.getCSManager();
        this.hierarchy = solver.getHierarchy();
        this.collectionClass = hierarchy.getJREClass("java.util.Collection");
        this.mapClass = hierarchy.getJREClass("java.util.Map");
    }

    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        JMethod method = csMethod.getMethod();
        // only containers handled by application code: library internals would cost a lot
        // and the summary only needs flows between application call sites
        if (method.isAbstract() || method.isNative() || !method.getDeclaringClass().isApplication()) {
            return;
        }
        Context ctx = csMethod.getContext();
        method.getIR().invokes(false).forEach(invoke -> register(ctx, invoke));
    }

    private void register(Context ctx, Invoke invoke) {
        if (!(invoke.getInvokeExp() instanceof InvokeInstanceExp exp)) {
            return;
        }
        MethodRef ref = invoke.getMethodRef();
        JClass cls = ref.getDeclaringClass();
        boolean isMap = hierarchy.isSubclass(mapClass, cls);
        if (!isMap && !hierarchy.isSubclass(collectionClass, cls)) {
            return;
        }
        String name = ref.getName();
        CSVar base = csManager.getCSVar(ctx, exp.getBase());
        int[] writes = (isMap ? MAP_WRITES : COLLECTION_WRITES).get(name);
        if (writes != null) {
            for (int i : writes) {
                int index = i < 0 ? exp.getArgCount() - 1 : i;
                if (index >= 0 && index < exp.getArgCount()) {
                    CSVar arg = csManager.getCSVar(ctx, exp.getArg(index));
                    writeArgToBase.computeIfAbsent(arg, k -> new ArrayList<>()).add(base);
                    writeBaseToArg.computeIfAbsent(base, k -> new ArrayList<>()).add(arg);
                    attach(solver.getPointsToSetOf(base), solver.getPointsToSetOf(arg));
                }
            }
        }
        Var result = invoke.getResult();
        if (result != null && (isMap ? MAP_READS : COLLECTION_READS).contains(name)) {
            CSVar res = csManager.getCSVar(ctx, result);
            readBaseToResult.computeIfAbsent(base, k -> new ArrayList<>()).add(res);
            subscribe(solver.getPointsToSetOf(base), res);
        }
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        for (CSVar base : writeArgToBase.getOrDefault(csVar, List.of())) {
            attach(solver.getPointsToSetOf(base), pts);
        }
        for (CSVar arg : writeBaseToArg.getOrDefault(csVar, List.of())) {
            attach(pts, solver.getPointsToSetOf(arg));
        }
        for (CSVar res : readBaseToResult.getOrDefault(csVar, List.of())) {
            subscribe(pts, res);
        }
    }

    private void attach(PointsToSet containers, PointsToSet values) {
        if (containers == null || values == null) {
            return;
        }
        List<CSObj> taints = values.objects().filter(ContainerTaint::isTaint).toList();
        if (taints.isEmpty()) {
            return;
        }
        for (CSObj container : containers) {
            if (isTaint(container) || !isTracked(container)) {
                continue; // tainted containers are handled by the YAML transfers
            }
            Set<CSObj> content = contents.computeIfAbsent(container, k -> new HashSet<>());
            PointsToSet added = solver.makePointsToSet();
            for (CSObj t : taints) {
                if (content.add(t)) {
                    added.addObject(t);
                }
            }
            if (!added.isEmpty()) {
                for (CSVar reader : readers.getOrDefault(container, Set.of())) {
                    solver.addPointsTo(reader, added);
                }
            }
        }
    }

    private void subscribe(PointsToSet containers, CSVar res) {
        if (containers == null) {
            return;
        }
        for (CSObj container : containers) {
            if (isTaint(container) || !isTracked(container) || !readers.computeIfAbsent(container, k -> new HashSet<>()).add(res)) {
                continue;
            }
            Set<CSObj> content = contents.get(container);
            if (content != null && !content.isEmpty()) {
                PointsToSet pts = solver.makePointsToSet();
                content.forEach(pts::addObject);
                solver.addPointsTo(res, pts);
            }
        }
    }

    /**
     * Only containers allocated in application code (and the per-call-site Spring Data results).
     * A container allocated inside a library (Stream.toList, Collectors.toMap, ...) or a shared
     * factory is one abstract object for the whole program under this heap abstraction, so taint
     * attached to it would reach every reader in the service (observed: ApiResponse.getData()).
     */
    private static boolean isTracked(CSObj obj) {
        if (obj.getObject() instanceof MockObj mock) {
            return mock.getDescriptor().string().startsWith("SpringData");
        }
        return obj.getObject().getContainerMethod()
                .map(m -> m.getDeclaringClass().isApplication())
                .orElse(false);
    }

    static boolean isTaint(CSObj obj) {
        return obj.getObject() instanceof MockObj mock
                && "TaintObj".equals(mock.getDescriptor().string());
    }
}

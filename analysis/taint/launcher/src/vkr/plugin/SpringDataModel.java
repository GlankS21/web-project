package vkr.plugin;

import micans.analysis.cs.VirtualPointer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ArrayType;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Models Spring Data repositories, which have no implementation in the analysed code
 * (Micans skips interface beans, so their call results stay empty and every flow through
 * an entity loaded from the database is lost).
 *
 * <p>The database is treated as a channel between requests, like a message queue, instead of
 * a heap object shared by all of them:
 * <ul>
 *   <li>every repository read returns a fresh row per call site and calling context, so what one
 *   request writes into its loaded entity is not seen by another request's entity;</li>
 *   <li>the sending end: entities passed to {@code save*} and every row read (managed entities are
 *   flushed by dirty checking) are collected, and each taint stored in one of their fields is
 *   reported as {@code StoreFlow{<source> -> <Entity: T field>}};</li>
 *   <li>the receiving end: loads of entity fields are taint sources of the configuration
 *   ({@code mk_entity_fields.py}), labelled {@code Entity.field}.</li>
 * </ul>
 * A summary composes the two field by field, so a value stored by one request reaches another
 * only through the field it was stored in.
 *
 * <p>Application calls on the mocked {@code Optional} ({@code filter}, {@code orElseThrow}, ...)
 * are modelled here as well: the field-sensitive load of {@code Optional.value} inside the JDK
 * does not return the rows to the caller (observed on order-service, cause not isolated).
 */
public class SpringDataModel implements Plugin {

    private static final Logger logger = LogManager.getLogger(SpringDataModel.class);

    private static final String REPOSITORY = "org.springframework.data.repository.Repository";

    private static final String ENTITY = "jakarta.persistence.Entity";

    /** classes whose fields are columns */
    private static final List<String> PERSISTENT = List.of(ENTITY,
            "jakarta.persistence.Embeddable", "jakarta.persistence.MappedSuperclass");

    private static final Descriptor ENTITY_DESC = () -> "SpringDataEntity";

    private static final Descriptor RESULT_DESC = () -> "SpringDataResult";

    private static final Pattern CLASS_NAME = Pattern.compile("[\\w$]+(?:[./][\\w$]+)+");

    /** how deep StoreFlow follows references from a stored entity (embeddables, child entities) */
    private static final int STORE_DEPTH = 4;

    private Solver solver;

    private CSManager csManager;

    private ClassHierarchy hierarchy;

    private TypeSystem typeSystem;

    private Context emptyContext;

    private JClass repository;

    private JClass optional;

    private JClass collection;

    private JField optionalValue;

    private JField arrayListData;

    /** entity -> the entities written to the database (saved or managed) */
    private final Map<JClass, VirtualPointer> stored = new HashMap<>();

    /** mocked Optional -> its row */
    private final Map<CSObj, CSObj> optionalRow = new HashMap<>();

    /** Optional methods returning the content */
    private static final Set<String> OPTIONAL_UNWRAP = Set.of("get", "orElseThrow", "orElse", "orElseGet");

    /** Optional methods returning the (same) Optional */
    private static final Set<String> OPTIONAL_KEEP = Set.of("filter", "or");

    /** base var of an application call on java.util.Optional -> (result var, method name) */
    private final Map<CSVar, List<Map.Entry<CSVar, String>>> optionalCalls = new HashMap<>();

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.csManager = solver.getCSManager();
        this.hierarchy = solver.getHierarchy();
        this.typeSystem = solver.getTypeSystem();
        this.emptyContext = solver.getContextSelector().getEmptyContext();
        this.repository = hierarchy.getClass(REPOSITORY);
        this.optional = hierarchy.getJREClass("java.util.Optional");
        this.collection = hierarchy.getJREClass("java.util.Collection");
        this.optionalValue = optional.getDeclaredField("value");
        this.arrayListData = hierarchy.getJREClass("java.util.ArrayList").getDeclaredField("elementData");
        if (repository == null) {
            logger.warn("{} not on the classpath, Spring Data model disabled", REPOSITORY);
        }
    }

    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        JMethod method = csMethod.getMethod();
        if (repository == null || method.isAbstract() || method.isNative()) {
            return;
        }
        Context ctx = csMethod.getContext();
        method.getIR().invokes(false).forEach(invoke -> handle(ctx, invoke));
        if (method.getDeclaringClass().isApplication()) {
            method.getIR().invokes(false).forEach(invoke -> registerOptionalCall(ctx, invoke));
        }
    }

    private void registerOptionalCall(Context ctx, Invoke invoke) {
        MethodRef ref = invoke.getMethodRef();
        String name = ref.getName();
        if (ref.getDeclaringClass() != optional || invoke.getResult() == null
                || !(OPTIONAL_UNWRAP.contains(name) || OPTIONAL_KEEP.contains(name))
                || !(invoke.getInvokeExp() instanceof InvokeInstanceExp exp)) {
            return;
        }
        CSVar base = csManager.getCSVar(ctx, exp.getBase());
        CSVar result = csManager.getCSVar(ctx, invoke.getResult());
        optionalCalls.computeIfAbsent(base, k -> new ArrayList<>()).add(Map.entry(result, name));
        applyOptionalCall(solver.getPointsToSetOf(base), result, name);
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        for (Map.Entry<CSVar, String> call : optionalCalls.getOrDefault(csVar, List.of())) {
            applyOptionalCall(pts, call.getKey(), call.getValue());
        }
    }

    private void applyOptionalCall(PointsToSet bases, CSVar result, String name) {
        if (bases == null) {
            return;
        }
        for (CSObj base : bases) {
            CSObj row = optionalRow.get(base);
            if (row == null) {
                continue;
            }
            solver.addPointsTo(result, OPTIONAL_UNWRAP.contains(name) ? row : base);
        }
    }

    private void handle(Context ctx, Invoke invoke) {
        MethodRef ref = invoke.getMethodRef();
        JClass repo = ref.getDeclaringClass();
        Var result = invoke.getResult();
        if (result == null || invoke.isStatic() || !repo.isInterface()
                || !hierarchy.isSubclass(repository, repo)) {
            return;
        }
        JMethod callee = ref.resolveNullable();
        if (callee != null && !callee.isAbstract()) {
            return; // default method with a body: analysed as usual
        }
        Type ret = ref.getReturnType();
        if (ref.getName().startsWith("save")
                && invoke.getInvokeExp() instanceof InvokeInstanceExp exp
                && exp.getArgCount() == 1) {
            JClass entity = entityOf(ret, callee, repo);
            Var arg = exp.getArg(0);
            solver.addPFGEdge(csManager.getCSVar(ctx, arg), csManager.getCSVar(ctx, result), FlowKind.OTHER);
            if (entity != null) {
                solver.addPFGEdge(csManager.getCSVar(ctx, arg), stored(entity), FlowKind.OTHER);
            }
            return;
        }
        JClass entity = entityOf(ret, callee, repo);
        if (entity == null || !(ret instanceof ClassType retType)) {
            if (entity == null && ret instanceof ClassType) {
                logger.info("No entity type found for repository call {}", ref);
            }
            return;
        }
        JClass retClass = retType.getJClass();
        if (retClass == entity) {
            solver.addVarPointsTo(ctx, result, row(ctx, invoke, entity));
        } else if (retClass == optional) {
            solver.addVarPointsTo(ctx, result, optionalOf(ctx, invoke, entity));
        } else if (retClass.getName().equals("java.lang.Iterable")
                || hierarchy.isSubclass(collection, retClass)) {
            solver.addVarPointsTo(ctx, result, listOf(ctx, invoke, entity));
        } else {
            logger.info("Unmodelled repository result {} of {}", ret, ref);
        }
    }

    @Override
    public void onFinish() {
        Set<String> flows = new TreeSet<>();
        stored.forEach((entity, pointer) -> {
            Set<CSObj> seen = new HashSet<>();
            for (CSObj obj : solver.getPointsToSetOf(pointer)) {
                collectStored(obj, STORE_DEPTH, seen, flows);
            }
            logger.info("stored {}: {} objects", entity.getName(), seen.size());
        });
        flows.forEach(System.out::println);
        logger.info("Detected {} store flow(s)", flows.size());
    }

    /** Reports every taint in a column (a field of a persistent class) reachable from a stored object. */
    private void collectStored(CSObj obj, int depth, Set<CSObj> seen, Set<String> flows) {
        if (depth == 0 || !seen.add(obj)) {
            return;
        }
        Type type = obj.getObject().getType();
        if (type instanceof ArrayType) {
            for (CSObj elem : solver.getPointsToSetOf(csManager.getArrayIndex(obj))) {
                collectStored(elem, depth - 1, seen, flows);
            }
            return;
        }
        if (!(type instanceof ClassType ct)) {
            return;
        }
        for (JClass c = ct.getJClass(); c != null; c = c.getSuperClass()) {
            JClass cls = c;
            boolean persistent = PERSISTENT.stream().anyMatch(cls::hasAnnotation);
            if (!persistent && !c.getName().startsWith("java.util.")) {
                continue; // a JDK collection holding child entities is walked through
            }
            for (JField f : c.getDeclaredFields()) {
                if (f.isStatic()) {
                    continue;
                }
                for (CSObj value : solver.getPointsToSetOf(csManager.getInstanceField(obj, f))) {
                    if (ContainerTaint.isTaint(value)) {
                        if (persistent) {
                            flows.add("StoreFlow{" + value.getObject().getAllocation() + " -> " + f + "}");
                        }
                    } else if (persistent || value.getObject().getType() instanceof ArrayType
                            || PERSISTENT.stream().anyMatch(a -> isAnnotated(value, a))) {
                        collectStored(value, depth - 1, seen, flows);
                    }
                }
            }
        }
    }

    private static boolean isAnnotated(CSObj obj, String annotation) {
        return obj.getObject().getType() instanceof ClassType ct && ct.getJClass().hasAnnotation(annotation);
    }

    private VirtualPointer stored(JClass entity) {
        return stored.computeIfAbsent(entity, e -> new VirtualPointer("stored:" + e.getName(), e.getType()));
    }

    /** A row read at this call site: external data, and written back when the entity is managed. */
    private CSObj row(Context ctx, Invoke invoke, JClass entity) {
        // functional: the solver ignores field stores and loads on non-functional mock objects
        Obj obj = solver.getHeapModel().getMockObj(ENTITY_DESC, invoke, entity.getType(), true);
        CSObj row = csManager.getCSObj(ctx, obj);
        solver.addPointsTo(stored(entity), row);
        return row;
    }

    private CSObj optionalOf(Context ctx, Invoke invoke, JClass entity) {
        CSObj row = row(ctx, invoke, entity);
        CSObj opt = mock(ctx, "Optional<" + entity.getName() + ">@" + invoke, optional.getType());
        if (optionalRow.put(opt, row) == null) {
            solver.addPointsTo(csManager.getInstanceField(opt, optionalValue), row);
        }
        return opt;
    }

    private CSObj listOf(Context ctx, Invoke invoke, JClass entity) {
        CSObj list = mock(ctx, "List<" + entity.getName() + ">@" + invoke,
                hierarchy.getJREClass("java.util.ArrayList").getType());
        CSObj data = mock(ctx, entity.getName() + "[]@" + invoke,
                typeSystem.getArrayType(typeSystem.getType("java.lang.Object"), 1));
        solver.addPointsTo(csManager.getInstanceField(list, arrayListData), data);
        solver.addPointsTo(csManager.getArrayIndex(data), row(ctx, invoke, entity));
        return list;
    }

    private CSObj mock(Context ctx, String alloc, Type type) {
        Obj obj = solver.getHeapModel().getMockObj(RESULT_DESC, alloc, type, true);
        return csManager.getCSObj(ctx, obj);
    }

    /**
     * The entity is the return type itself, else the first {@code @Entity} class named in the
     * generic return type of the method, else in the generic signature of the repository.
     */
    private JClass entityOf(Type ret, JMethod callee, JClass repo) {
        if (ret instanceof ClassType ct && ct.getJClass().hasAnnotation(ENTITY)) {
            return ct.getJClass();
        }
        if (callee != null && callee.getGSignature() != null) {
            JClass e = firstEntity(callee.getGSignature().getResultSignature().toString());
            if (e != null) {
                return e;
            }
        }
        for (JClass c = repo; c != null; ) {
            if (c.getGSignature() != null) {
                JClass e = firstEntity(c.getGSignature().toString());
                if (e != null) {
                    return e;
                }
            }
            c = c.getInterfaces().stream().filter(i -> hierarchy.isSubclass(repository, i)).findFirst().orElse(null);
        }
        return null;
    }

    private JClass firstEntity(String signature) {
        Matcher m = CLASS_NAME.matcher(signature);
        while (m.find()) {
            String name = m.group().replace('/', '.');
            JClass c = hierarchy.getClass(name);
            if (c == null && name.startsWith("L")) {
                c = hierarchy.getClass(name.substring(1)); // descriptor form: Lcom/x/Y;
            }
            if (c != null && c.hasAnnotation(ENTITY)) {
                return c;
            }
        }
        return null;
    }
}

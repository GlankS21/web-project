package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.ir.exp.Var;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.TreeMap;

/**
 * Prints, for every variable of methods whose signature contains one of the comma-separated
 * substrings in system property {@code vkr.debug}, how many objects and taint objects it points to;
 * for the variables named in {@code vkr.debug.var} (e.g. {@code $r11}), every object with its
 * allocation site.
 */
public class DebugDump implements Plugin {

    private Solver solver;

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
    }

    @Override
    public void onFinish() {
        String filter = System.getProperty("vkr.debug");
        if (filter == null || filter.isBlank()) {
            return;
        }
        String[] keys = filter.replace('_', ' ').split(","); // '_' stands for a space
        Map<String, int[]> rows = new TreeMap<>();
        Map<String, TreeSet<String>> appObjs = new TreeMap<>();
        Map<String, TreeSet<String>> taints = new TreeMap<>();
        Map<String, String> defs = new TreeMap<>(); // var -> its defining statement // context + source of each taint object
        for (CSVar csVar : solver.getCSManager().getCSVars()) {
            Var var = csVar.getVar();
            String method = var.getMethod().getSignature();
            boolean match = false;
            for (String k : keys) {
                match |= method.contains(k);
            }
            if (!match) {
                continue;
            }
            String def = var.getMethod().getIR().stmts()
                    .filter(st -> st.getDef().filter(d -> d == var).isPresent())
                    .map(st -> "  @ " + st).findFirst().orElse("");
            defs.put(method + " / " + var.getName(), def);
            int[] row = rows.computeIfAbsent(method + " / " + var.getName(), k -> new int[4]);
            for (var obj : solver.getPointsToSetOf(csVar)) {
                row[0]++;
                if (ContainerTaint.isTaint(obj)) {
                    row[1]++;
                    taints.computeIfAbsent(method + " / " + var.getName(), k -> new TreeSet<>())
                            .add(csVar.getContext() + " " + obj.getObject().getAllocation());
                }
                if (obj.getObject().getType().getName().startsWith("com.shop.")) {
                    row[2]++; // application-typed objects (e.g. entities)
                    appObjs.computeIfAbsent(method + " / " + var.getName(), k -> new TreeSet<>())
                            .add(obj.getObject().getType().getName());
                }
                if (obj.getObject() instanceof pascal.taie.analysis.pta.core.heap.MockObj m
                        && m.getDescriptor().string().startsWith("SpringData")) {
                    row[3]++;
                }
            }
        }
        System.out.println("=== vkr.debug points-to (objects / taints / app-typed / spring-data mocks)");
        rows.forEach((k, v) -> System.out.println("DBG " + v[0] + " / " + v[1] + " / " + v[2] + " / " + v[3] + "  " + k
                + (appObjs.containsKey(k) && appObjs.get(k).size() <= 12 ? "  " + appObjs.get(k) : "")
                + defs.getOrDefault(k, "")));
        taints.forEach((k, v) -> v.forEach(t -> System.out.println("DBGT " + k + "  <-  " + t)));
        String objsOf = System.getProperty("vkr.debug.var"); // every object of these variables
        if (objsOf != null) {
            List<String> names = List.of(objsOf.split(","));
            for (CSVar csVar : solver.getCSManager().getCSVars()) {
                Var var = csVar.getVar();
                if (names.contains(var.getName()) && rows.containsKey(var.getMethod().getSignature() + " / " + var.getName())) {
                    for (var obj : solver.getPointsToSetOf(csVar)) {
                        System.out.println("DBGO " + var.getMethod().getName() + "/" + var.getName() + "  "
                                + obj.getObject().getType().getName() + "  " + obj.getContext() + " "
                                + obj.getObject().getAllocation() + taintedFields(obj));
                    }
                }
            }
        }
    }

    /** instance fields of the object (superclasses included) that hold taint objects */
    private String taintedFields(pascal.taie.analysis.pta.core.cs.element.CSObj obj) {
        StringBuilder out = new StringBuilder();
        if (obj.getObject().getType() instanceof pascal.taie.language.type.ClassType type) {
            for (var c = type.getJClass(); c != null; c = c.getSuperClass()) {
                for (var f : c.getDeclaredFields()) {
                    if (f.isStatic()) {
                        continue;
                    }
                    var pts = solver.getPointsToSetOf(solver.getCSManager().getInstanceField(obj, f));
                    long n = pts == null ? 0 : pts.objects().filter(ContainerTaint::isTaint).count();
                    if (n > 0) {
                        out.append("  TAINTED ").append(f.getName()).append('=').append(n);
                    }
                }
            }
        }
        return out.toString();
    }
}

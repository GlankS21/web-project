package vkr.plugin;

import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.ir.exp.Var;

import java.util.Map;
import java.util.TreeSet;
import java.util.TreeMap;

/**
 * Prints, for every variable of methods whose signature contains one of the comma-separated
 * substrings in system property {@code vkr.debug}, how many objects and taint objects it points to.
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
    }
}

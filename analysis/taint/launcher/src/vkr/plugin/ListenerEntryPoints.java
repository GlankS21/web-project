package vkr.plugin;

import micans.plugin.spring.Bean;
import micans.plugin.spring.MicroserviceHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.solver.DeclaredParamProvider;
import pascal.taie.analysis.pta.core.solver.EntryPoint;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.core.solver.SpecifiedParamProvider;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;

import java.util.Set;

/**
 * Makes every {@code @RabbitListener} method of the analysed service an entry point.
 *
 * <p>Micans reaches a listener only through a publisher in the analysed system. A per-service
 * summary has no publisher (it lives in another service), so without this the listener is
 * dead code and the incoming channel has no summary. The payload parameter is mocked from its
 * declared type, and {@code this} is the listener's Spring bean so injected fields resolve.
 */
public class ListenerEntryPoints implements Plugin {

    private static final Logger logger = LogManager.getLogger(ListenerEntryPoints.class);

    private static final String RABBIT_LISTENER = "org.springframework.amqp.rabbit.annotation.RabbitListener";

    private Solver solver;

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
    }

    @Override
    public void onStart() {
        Context empty = solver.getContextSelector().getEmptyContext();
        int count = 0;
        for (MicroserviceHolder holder : MicroserviceHolder.getAllHolders()) {
            for (Bean bean : holder.getBeanManager().getAllBeans()) {
                JClass cls = bean.getJClass();
                for (JMethod m : cls.getDeclaredMethods()) {
                    if (m.isAbstract() || !m.hasAnnotation(RABBIT_LISTENER)) {
                        continue;
                    }
                    var params = new SpecifiedParamProvider.Builder(m)
                            .setDelegate(new DeclaredParamProvider(m, solver.getHeapModel(), 1))
                            .addThisObj(Set.of())
                            .build();
                    solver.addEntryPoint(new EntryPoint(m, params));
                    solver.addPFGEdge(bean.getPointer(),
                            solver.getCSManager().getCSVar(empty, m.getIR().getThis()),
                            FlowKind.THIS_PASSING);
                    count++;
                }
            }
        }
        logger.info("Added {} @RabbitListener entry point(s)", count);
    }
}

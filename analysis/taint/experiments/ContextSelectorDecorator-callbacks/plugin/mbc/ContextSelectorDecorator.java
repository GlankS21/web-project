package micans.plugin.mbc;


import micans.plugin.spring.MicroserviceHolder;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.context.ContextFactory;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.selector.AbstractContextSelector;
import pascal.taie.analysis.pta.core.cs.selector.ContextSelector;
import pascal.taie.analysis.pta.core.cs.selector.SelectiveSelector;
import pascal.taie.analysis.pta.core.heap.NewObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Sets;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * for modeling the RabbitMQ Binding
 *
 * <p>VKR addition (shadows the Micans class on the launcher classpath): JDK callbacks. An
 * application lambda passed to {@code forEach} or a stream is invoked from a single call site
 * inside the JDK, so under 1-call every element of every collection iterated that way meets in
 * the lambda's parameter. Calls into the {@code forEach}/{@code forEachRemaining} chain and from
 * it back into an application lambda keep the call string (k = {@link #limit}), so the lambda is
 * analysed per application call site of the iteration. Stream pipelines are not covered.
 */
public class ContextSelectorDecorator implements ContextSelector {

    private static final String STREAM_PACKAGE = "java.util.stream.";

    private static final Set<String> ITERATION_METHODS = Set.of("forEach", "forEachRemaining");

    /** cache of {@link ContextSelectorDecorator#isIteration(JMethod)} */
    private final transient Map<JMethod, Boolean> iterationCache = Maps.newMap();

    private boolean isIteration(JMethod m) {
        return iterationCache.computeIfAbsent(m, k -> {
            String cls = k.getDeclaringClass().getName();
            // the whole stream package (STREAM_PACKAGE) costs > 20 min on order-service
            return cls.startsWith("java.util.") && ITERATION_METHODS.contains(k.getName());
        });
    }

    /** A call along a JDK iteration, or from the JDK back into an application lambda. */
    private boolean isCallback(CSCallSite callSite, JMethod callee) {
        JMethod caller = callSite.getContainer().getMethod();
        if (isIteration(callee)) {
            return true;
        }
        return callee.getName().startsWith("lambda$")
                && MicroserviceHolder.isApplication(callee.getDeclaringClass().getName())
                && isIteration(caller);
    }

    private static final Collection<String> CONCERNED_CLASS_PREFIXES = List.of(
            "org.springframework.amqp.core.AbstractBuilder",
            "org.springframework.amqp.core.AbstractDeclarable",
            "org.springframework.amqp.core.AbstractExchange",
            "org.springframework.amqp.core.Binding",
            "org.springframework.amqp.core.BindingBuilder",
            "org.springframework.amqp.core.CustomExchange",
            "org.springframework.amqp.core.DirectExchange",
            "org.springframework.amqp.core.Exchange",
            "org.springframework.amqp.core.ExchangeBuilder",
            "org.springframework.amqp.core.FanoutExchange",
            "org.springframework.amqp.core.HeadersExchange",
            "org.springframework.amqp.core.Queue",
            "org.springframework.amqp.core.QueueBuilder",
            "org.springframework.amqp.core.QueueInformation",
            "org.springframework.amqp.core.TopicExchange"
    );

    private static final int limit = 4;

    private static final int hlimit = limit - 1;

    private final ContextSelector selector;

    private final ContextFactory<Object> factory;

    /**
     * cache of {@link ContextSelectorDecorator#isConcerned(JClass)}
     */
    private final transient Map<JClass, Boolean> cCache = Maps.newMap();

    private boolean isConcerned(@Nullable JClass jClass) {
        if (jClass == null) {
            return false;
        }
        return cCache.computeIfAbsent(jClass, c -> {
            for (String packageName : CONCERNED_CLASS_PREFIXES) {
                if (c.getName().startsWith(packageName)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * cache of {@link ContextSelectorDecorator#isConcerned(JMethod)}
     */
    private final transient Map<JMethod, Boolean> mCache = Maps.newMap();

    private boolean isConcerned(@Nullable JMethod jMethod) {
        if (jMethod == null) {
            return false;
        }
        return mCache.computeIfAbsent(jMethod, m -> {
            // enum
            JClass declaringClass = m.getDeclaringClass();
            if (declaringClass.isEnum() && MicroserviceHolder
                    .isApplication(declaringClass.getName())) {
                return true;
            }
            // rabbitmq
            Set<String> classNames = Sets.newSet();
            classNames.add(declaringClass.getName());
            for (Type paramType : m.getParamTypes()) {
                if (paramType instanceof ClassType classType) {
                    classNames.add(classType.getName());
                }
            }
            if (m.getReturnType() instanceof ClassType classType) {
                classNames.add(classType.getName());
            }
            for (String className : classNames) {
                for (String packageName : CONCERNED_CLASS_PREFIXES) {
                    if (className.startsWith(packageName)) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    /**
     * cache of {@link ContextSelectorDecorator#isAppEnumParam(JMethod)}
     */
    private final transient Map<JMethod, Boolean> mCache2 = Maps.newMap();

    private boolean isAppEnumParam(@Nullable JMethod jMethod) {
        if (jMethod == null) {
            return false;
        }
        return mCache2.computeIfAbsent(jMethod, m -> {
            for (Type paramType : m.getParamTypes()) {
                if (paramType instanceof ClassType classType) {
                    if (classType.getJClass().isEnum() && MicroserviceHolder
                            .isApplication(classType.getName())) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @SuppressWarnings("unchecked")
    public ContextSelectorDecorator(ContextSelector selector) {
        this.selector = selector;
        if (selector instanceof AbstractContextSelector<?> abstractContextSelector) {
            this.factory = (ContextFactory<Object>) abstractContextSelector.getFactory();
        } else if (selector instanceof SelectiveSelector selectiveSelector) {
            this.factory = (ContextFactory<Object>) ((AbstractContextSelector<?>) selectiveSelector.delegate)
                    .getFactory();
        } else {
            throw new IllegalArgumentException("unsupported selector type: " + selector);
        }
    }

    @Override
    public Context getEmptyContext() {
        return selector.getEmptyContext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Context selectHeapContext(CSMethod method, Obj obj) {
        if (obj instanceof NewObj) {
            return selectNewObjContext(method, (NewObj) obj);
        } else {
            return getEmptyContext();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Context selectContext(CSCallSite callSite, JMethod callee) {
        if (isConcerned(callSite.getContainer().getMethod())
                || isConcerned(callee) || isCallback(callSite, callee)) {
            // use call-site sensitive selector for static calls
            return factory.append(callSite.getContext(), callSite.getCallSite(), limit);
        }
        return selector.selectContext(callSite, callee);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Context selectContext(CSCallSite callSite, CSObj recv, JMethod callee) {
        if (isAppEnumParam(callee) || isCallback(callSite, callee)) {
            // use call-site sensitive selector for instance call target with enum parameter
            return factory.append(callSite.getContext(), callSite.getCallSite(), limit);
        }
        if (isConcerned(callSite.getContainer().getMethod())
                || isConcerned(callee)
                || (recv.getObject().getType() instanceof JClass jClass && isConcerned(jClass))) {
            // use object-site sensitive selector for instance calls
            return factory.append(recv.getContext(), recv.getObject(), limit);
        }
        return selector.selectContext(callSite, recv, callee);
    }

    protected Context selectNewObjContext(CSMethod method, NewObj obj) {
        if (isConcerned(method.getMethod())
                || (obj.getType() instanceof JClass jClass && isConcerned(jClass))) {
            // use object-site sensitive heap
            return factory.makeLastK(method.getContext(), hlimit);
        }
        return selector.selectHeapContext(method, obj);
    }

}

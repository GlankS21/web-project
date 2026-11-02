package ru.itmo.vkr.channels;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import ru.itmo.vkr.channels.model.CodeRef;
import ru.itmo.vkr.channels.model.Listener;
import ru.itmo.vkr.channels.model.Publisher;
import ru.itmo.vkr.channels.model.QueueBinding;
import ru.itmo.vkr.channels.model.Unresolved;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Spring AMQP publishers, queue bindings and listeners of one service.
 * <p>
 * Besides direct {@code RabbitTemplate} sends, events written to a transactional outbox are
 * modelled as publishers at the place they are enqueued: the relay job that later forwards them
 * sends values read back from the database, which cannot be resolved statically.
 */
final class AmqpExtractor {

    record Result(List<Publisher> publishers, List<QueueBinding> bindings, List<Listener> listeners,
                  List<Unresolved> unresolved) {
    }

    /**
     * A method that publishes an event through an outbox, and the positions of its arguments.
     */
    record OutboxSignature(String receiverTypeSuffix, String method, int exchangeArg, int routingKeyArg,
                           int payloadArg) {
    }

    static final List<OutboxSignature> DEFAULT_OUTBOX_SIGNATURES = List.of(
            new OutboxSignature("OutboxService", "enqueue", 0, 1, 2));

    private static final Set<String> TEMPLATE_TYPES = Set.of(
            "RabbitTemplate", "AmqpTemplate", "RabbitOperations", "RabbitMessagingTemplate");
    private static final Set<String> TEMPLATE_METHODS = Set.of(
            "convertAndSend", "send", "convertSendAndReceive", "convertSendAndReceiveAsType");
    private static final Set<String> NON_PAYLOAD_TYPES = Set.of("Message", "Channel", "MessageHeaders");
    /** Exchange name of the AMQP default exchange, which routes by queue name. */
    static final String DEFAULT_EXCHANGE = "";

    private final Path root;
    private final ValueResolver resolver;
    private final List<OutboxSignature> outboxSignatures;

    AmqpExtractor(Path root, ValueResolver resolver, List<OutboxSignature> outboxSignatures) {
        this.root = root;
        this.resolver = resolver;
        this.outboxSignatures = outboxSignatures;
    }

    Result extract(SourceIndex.Module module) {
        Set<Publisher> publishers = new LinkedHashSet<>();
        Set<QueueBinding> bindings = new LinkedHashSet<>();
        List<Listener> listeners = new ArrayList<>();
        Set<Unresolved> unresolved = new LinkedHashSet<>();
        for (CompilationUnit unit : module.units()) {
            for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                publisher(module, call, publishers, unresolved);
                builderBinding(module, call, bindings, unresolved);
            }
            for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
                if (creation.getType().getNameAsString().equals("Binding") && creation.getArguments().size() >= 4) {
                    addBindings(module, creation, creation.getArgument(0), creation.getArgument(2),
                            creation.getArgument(3), bindings, unresolved);
                }
            }
            listeners(module, unit, listeners, bindings, unresolved);
        }
        return new Result(List.copyOf(publishers), List.copyOf(bindings), listeners, List.copyOf(unresolved));
    }

    // ------------------------------------------------------------------ publishers

    private void publisher(SourceIndex.Module module, MethodCallExpr call, Set<Publisher> out,
                           Set<Unresolved> unresolved) {
        String receiver = resolver.receiverType(call).orElse(null);
        if (receiver == null) {
            return;
        }
        String name = call.getNameAsString();
        NodeList<Expression> args = call.getArguments();
        Expression exchange;
        Expression routingKey;
        Expression payload;
        String mechanism;
        if (TEMPLATE_TYPES.contains(receiver) && TEMPLATE_METHODS.contains(name)) {
            mechanism = "RABBIT_TEMPLATE";
            // convertAndSend(exchange, routingKey, message, ...) vs convertAndSend(routingKey, message, postProcessor)
            boolean explicitExchange = args.size() >= 3
                    && !(args.get(2).isLambdaExpr() || args.get(2).isMethodReferenceExpr());
            if (explicitExchange) {
                exchange = args.get(0);
                routingKey = args.get(1);
                payload = args.get(2);
            } else if (args.size() >= 2) {
                exchange = null;
                routingKey = args.get(0);
                payload = args.get(1);
            } else {
                return;
            }
        } else {
            Optional<OutboxSignature> signature = outboxSignatures.stream()
                    .filter(s -> receiver.endsWith(s.receiverTypeSuffix()) && s.method().equals(name))
                    .filter(s -> args.size() > Math.max(s.payloadArg(), Math.max(s.exchangeArg(), s.routingKeyArg())))
                    .findFirst();
            if (signature.isEmpty()) {
                return;
            }
            mechanism = "OUTBOX";
            exchange = args.get(signature.get().exchangeArg());
            routingKey = args.get(signature.get().routingKeyArg());
            payload = args.get(signature.get().payloadArg());
        }

        CodeRef sendSite = Ast.ref(call, root);
        for (ValueResolver.Ctx ctx : resolver.contextsFor(call)) {
            MethodCallExpr originCall = ctx.originSite();
            CodeRef origin = originCall == null ? sendSite : Ast.ref(originCall, root);
            Set<String> exchanges = exchange == null ? Set.of(DEFAULT_EXCHANGE) : resolver.strings(exchange, ctx);
            Set<String> keys = resolver.strings(routingKey, ctx);
            if (exchanges.isEmpty() || keys.isEmpty()) {
                boolean relay = Ast.enclosingType(call).getNameAsString().contains("Outbox");
                unresolved.add(new Unresolved(module.name(), relay ? "PUBLISHER_RELAY" : "PUBLISHER",
                        (relay ? "outbox relay, its events are modelled where they are enqueued: " : "")
                                + "exchange/routing key not statically known in " + call, origin));
                continue;
            }
            String payloadType = resolver.typeOf(payload, ctx).orElse(null);
            for (String ex : exchanges) {
                for (String key : keys) {
                    String id = "pub:" + module.name() + ":" + origin.label() + ":" + ex + "/" + key;
                    out.add(new Publisher(id, module.name(), mechanism, ex, key, payloadType, origin, sendSite));
                }
            }
        }
    }

    // ------------------------------------------------------------------ bindings

    /**
     * {@code BindingBuilder.bind(queue).to(exchange).with(key)}; a binding without {@code with}
     * (fanout exchange) receives every routing key.
     */
    private void builderBinding(SourceIndex.Module module, MethodCallExpr to, Set<QueueBinding> out,
                                Set<Unresolved> unresolved) {
        if (!to.getNameAsString().equals("to") || to.getArguments().size() != 1) {
            return;
        }
        Optional<MethodCallExpr> bind = to.getScope()
                .filter(Expression::isMethodCallExpr)
                .map(Expression::asMethodCallExpr)
                .filter(call -> call.getNameAsString().equals("bind") && call.getArguments().size() == 1)
                .filter(call -> call.getScope().map(s -> Ast.simpleName(s.toString()).equals("BindingBuilder")).orElse(false));
        if (bind.isEmpty()) {
            return;
        }
        Expression key = to.getParentNode()
                .filter(MethodCallExpr.class::isInstance)
                .map(MethodCallExpr.class::cast)
                .filter(with -> with.getNameAsString().equals("with") && with.getArguments().isNonEmpty())
                .filter(with -> with.getScope().filter(scope -> scope == to).isPresent())
                .map(with -> with.getArgument(0))
                .orElse(null);
        addBindings(module, to, bind.get().getArgument(0), to.getArgument(0), key, out, unresolved);
    }

    private void addBindings(SourceIndex.Module module, Node site, Expression queue, Expression exchange,
                             Expression key, Set<QueueBinding> out, Set<Unresolved> unresolved) {
        for (ValueResolver.Ctx ctx : resolver.contextsFor(site)) {
            MethodCallExpr originCall = ctx.originSite();
            CodeRef ref = Ast.ref(originCall == null ? site : originCall, root);
            Set<String> queues = resolver.strings(queue, ctx);
            Set<String> exchanges = resolver.strings(exchange, ctx);
            Set<String> keys = key == null ? Set.of("#") : resolver.strings(key, ctx);
            if (queues.isEmpty() || exchanges.isEmpty() || keys.isEmpty()) {
                unresolved.add(new Unresolved(module.name(), "BINDING",
                        "queue, exchange or binding key not statically known in " + site, ref));
                continue;
            }
            for (String q : queues) {
                for (String ex : exchanges) {
                    for (String k : keys) {
                        out.add(new QueueBinding(module.name(), q, ex, k, ref));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ listeners

    private void listeners(SourceIndex.Module module, CompilationUnit unit, List<Listener> out,
                           Set<QueueBinding> bindings, Set<Unresolved> unresolved) {
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            Optional<AnnotationExpr> classListener = Ast.annotation(type, "RabbitListener");
            for (MethodDeclaration method : type.getMethods()) {
                Optional<AnnotationExpr> methodListener = Ast.annotation(method, "RabbitListener");
                if (methodListener.isPresent()) {
                    addListeners(module, methodListener.get(), method, "METHOD", out, bindings, unresolved);
                } else if (classListener.isPresent() && Ast.hasAnnotation(method, "RabbitHandler")) {
                    addListeners(module, classListener.get(), method, "TYPE", out, bindings, unresolved);
                }
            }
        }
    }

    private void addListeners(SourceIndex.Module module, AnnotationExpr listener, MethodDeclaration handler,
                              String dispatch, List<Listener> out, Set<QueueBinding> bindings,
                              Set<Unresolved> unresolved) {
        CodeRef ref = Ast.ref(handler, root);
        Set<String> queues = new LinkedHashSet<>();
        for (Expression queue : Ast.attribute(listener, "queues", "queuesToDeclare")) {
            queues.addAll(names(queue));
        }
        // declarative topology: @RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange, key = ...))
        for (Expression declared : Ast.attribute(listener, "bindings")) {
            if (!(declared instanceof AnnotationExpr queueBinding)) {
                continue;
            }
            Set<String> boundQueues = names(Ast.attribute(queueBinding, "value"));
            Set<String> exchanges = names(Ast.attribute(queueBinding, "exchange"));
            Set<String> keys = names(Ast.attribute(queueBinding, "key"));
            queues.addAll(boundQueues);
            for (String q : boundQueues) {
                for (String ex : exchanges) {
                    for (String k : keys.isEmpty() ? Set.of("#") : keys) {
                        bindings.add(new QueueBinding(module.name(), q, ex, k, ref));
                    }
                }
            }
        }
        queues.removeIf(q -> q.contains("${") || q.contains("#{"));
        if (queues.isEmpty()) {
            unresolved.add(new Unresolved(module.name(), "LISTENER",
                    "queue names not statically known in " + listener, ref));
            return;
        }
        Optional<Parameter> payload = payloadParameter(handler);
        for (String queue : queues) {
            out.add(new Listener("lis:" + module.name() + ":" + ref.label() + ":" + queue, module.name(), queue,
                    dispatch, payload.map(p -> Ast.typeName(p.getType())).orElse(null),
                    payload.map(Parameter::getNameAsString).orElse(null), ref));
        }
    }

    private Set<String> names(List<Expression> expressions) {
        Set<String> names = new LinkedHashSet<>();
        expressions.forEach(e -> names.addAll(names(e)));
        return names;
    }

    /**
     * Names given by a string expression or by a {@code @Queue}/{@code @Exchange} annotation.
     */
    private Set<String> names(Expression expression) {
        if (expression instanceof AnnotationExpr annotation) {
            return names(Ast.attribute(annotation, "value", "name"));
        }
        return resolver.strings(expression, ValueResolver.Ctx.root(null));
    }

    private static Optional<Parameter> payloadParameter(MethodDeclaration handler) {
        Optional<Parameter> annotated = handler.getParameters().stream()
                .filter(p -> Ast.hasAnnotation(p, "Payload"))
                .findFirst();
        if (annotated.isPresent()) {
            return annotated;
        }
        return handler.getParameters().stream()
                .filter(p -> !Ast.hasAnnotation(p, "Header") && !Ast.hasAnnotation(p, "Headers"))
                .filter(p -> !NON_PAYLOAD_TYPES.contains(Ast.typeName(p.getType())))
                .findFirst();
    }
}

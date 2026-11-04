package ru.itmo.vkr.channels;

import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Statically evaluates the set of strings an expression can take, and the type of the object it
 * denotes, by following constants, string concatenation, local variables, private helper methods
 * and the arguments their callers pass.
 * <p>
 * Values that depend on runtime input come back as an empty set; callers report those as
 * unresolved rather than guess.
 */
final class ValueResolver {

    /**
     * Arguments a caller passed for one parameter (several for a varargs parameter), with the
     * context they must be evaluated in.
     */
    record BoundArg(List<Expression> expressions, Ctx ctx) {
    }

    /**
     * An evaluation context: the method whose body is being evaluated and, when it was reached
     * through a particular call, the arguments of that call. Resolving all the expressions of one
     * site in the same context keeps the values correlated: a queue is paired only with the
     * routing keys passed together with it.
     */
    record Ctx(CallableDeclaration<?> method, Map<String, BoundArg> bindings, Ctx parent,
               MethodCallExpr callSite, int depth) {

        static Ctx root(CallableDeclaration<?> method) {
            return new Ctx(method, Map.of(), null, null, 0);
        }

        Ctx detached() {
            return new Ctx(null, Map.of(), null, null, depth + 1);
        }

        /**
         * The call site furthest up the caller chain, where the bound values were written.
         */
        MethodCallExpr originSite() {
            MethodCallExpr site = callSite;
            for (Ctx c = parent; c != null; c = c.parent()) {
                if (c.callSite() != null) {
                    site = c.callSite();
                }
            }
            return site;
        }
    }

    private static final int MAX_DEPTH = 12;
    private static final int MAX_CALLER_LEVELS = 4;
    private static final int MAX_VALUES = 64;

    /** Static factories of Spring AMQP builders whose first argument is the declared name. */
    private static final Set<String> NAME_FACTORIES = Set.of(
            "topicExchange", "directExchange", "fanoutExchange", "headersExchange", "durable", "nonDurable");
    /** Spring AMQP declarables whose first constructor argument is the name. */
    private static final Set<String> NAMED_DECLARABLES = Set.of(
            "Queue", "TopicExchange", "DirectExchange", "FanoutExchange", "HeadersExchange");

    /** Parameter type of implicitly typed callbacks, keyed by {@code ReceiverType#method}. */
    private static final Map<String, String> CALLBACK_PARAMETER_TYPES = Map.of(
            "RabbitTemplate#invoke", "RabbitOperations",
            "RabbitOperations#invoke", "RabbitOperations");

    private final SourceIndex index;

    ValueResolver(SourceIndex index) {
        this.index = index;
    }

    // ------------------------------------------------------------------ contexts

    /**
     * The contexts {@code node} has to be evaluated in: one per call chain that reaches its
     * enclosing method from within the same class, or a single context without bindings when the
     * method is an entry point (public API, bean method, listener...).
     */
    List<Ctx> contextsFor(Node node) {
        return contextsFor(node, 0);
    }

    private List<Ctx> contextsFor(Node node, int level) {
        CallableDeclaration<?> method = Ast.enclosingCallable(node);
        if (method == null) {
            return List.of(Ctx.root(null));
        }
        if (method.getParameters().isEmpty() || level >= MAX_CALLER_LEVELS) {
            return List.of(Ctx.root(method));
        }
        List<MethodCallExpr> sites = localCallSites(method);
        if (sites.isEmpty()) {
            return List.of(Ctx.root(method));
        }
        List<Ctx> contexts = new ArrayList<>();
        for (MethodCallExpr site : sites) {
            for (Ctx parent : contextsFor(site, level + 1)) {
                contexts.add(new Ctx(method, bind(method, site.getArguments(), parent), parent, site,
                        parent.depth() + 1));
            }
        }
        return contexts;
    }

    private static List<MethodCallExpr> localCallSites(CallableDeclaration<?> method) {
        if (!(method instanceof MethodDeclaration)) {
            return List.of();
        }
        TypeDeclaration<?> type = Ast.enclosingType(method);
        if (type == null) {
            return List.of();
        }
        return type.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals(method.getNameAsString()))
                .filter(call -> call.getScope().map(Expression::isThisExpr).orElse(true))
                .filter(call -> accepts(method, call.getArguments().size()))
                .filter(call -> !method.isAncestorOf(call))
                .toList();
    }

    private static boolean accepts(CallableDeclaration<?> method, int argumentCount) {
        int params = method.getParameters().size();
        boolean varArgs = params > 0 && method.getParameter(params - 1).isVarArgs();
        return varArgs ? argumentCount >= params - 1 : argumentCount == params;
    }

    private static Map<String, BoundArg> bind(CallableDeclaration<?> method, List<Expression> args, Ctx callerCtx) {
        Map<String, BoundArg> bindings = new HashMap<>();
        List<Parameter> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            Parameter param = params.get(i);
            List<Expression> values;
            if (i >= args.size()) {
                values = List.of();
            } else if (param.isVarArgs()) {
                values = List.copyOf(args.subList(i, args.size()));
            } else {
                values = List.of(args.get(i));
            }
            bindings.put(param.getNameAsString(), new BoundArg(values, callerCtx));
        }
        return bindings;
    }

    // ------------------------------------------------------------------ string values

    Set<String> strings(Expression expr, Ctx ctx) {
        if (ctx.depth() > MAX_DEPTH) {
            return Set.of();
        }
        if (expr instanceof StringLiteralExpr literal) {
            return Set.of(literal.asString());
        }
        if (expr instanceof TextBlockLiteralExpr textBlock) {
            return Set.of(textBlock.asString());
        }
        if (expr instanceof IntegerLiteralExpr integer) {
            return Set.of(integer.getValue());
        }
        if (expr instanceof EnclosedExpr enclosed) {
            return strings(enclosed.getInner(), ctx);
        }
        if (expr instanceof CastExpr cast) {
            return strings(cast.getExpression(), ctx);
        }
        if (expr instanceof ConditionalExpr conditional) {
            return union(strings(conditional.getThenExpr(), ctx), strings(conditional.getElseExpr(), ctx));
        }
        if (expr instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            return concat(strings(binary.getLeft(), ctx), strings(binary.getRight(), ctx));
        }
        if (expr instanceof NameExpr name) {
            return name(name, ctx);
        }
        if (expr instanceof FieldAccessExpr field) {
            return fieldAccess(field, ctx);
        }
        if (expr instanceof MethodCallExpr call) {
            return call(call, ctx);
        }
        if (expr instanceof ObjectCreationExpr creation
                && NAMED_DECLARABLES.contains(creation.getType().getNameAsString())
                && creation.getArguments().isNonEmpty()) {
            return strings(creation.getArgument(0), ctx);
        }
        return Set.of();
    }

    private Set<String> name(NameExpr name, Ctx ctx) {
        String id = name.getNameAsString();
        CallableDeclaration<?> owner = Ast.enclosingCallable(name);
        if (owner != null) {
            Optional<VariableDeclarator> local = localDeclarator(name, owner);
            if (local.isPresent()) {
                VariableDeclarator variable = local.get();
                Optional<ForEachStmt> loop = forEachOf(variable);
                if (loop.isPresent()) {
                    return elements(loop.get().getIterable(), ctx);
                }
                return variable.getInitializer().map(init -> strings(init, ctx)).orElse(Set.of());
            }
            BoundArg bound = bound(id, owner, ctx);
            if (bound != null) {
                Set<String> values = new LinkedHashSet<>();
                bound.expressions().forEach(e -> values.addAll(strings(e, bound.ctx())));
                return values;
            }
            if (owner.getParameterByName(id).isPresent()) {
                return Set.of();
            }
        }
        Optional<Set<String>> field = fieldValue(Ast.enclosingType(name), id, ctx);
        return field.orElseGet(() -> staticImport(name, ctx));
    }

    /**
     * Values of the elements of an iterable: the arguments of a varargs parameter, an array
     * initialiser or {@code List.of(...)}.
     */
    private Set<String> elements(Expression iterable, Ctx ctx) {
        Set<String> values = new LinkedHashSet<>();
        if (iterable instanceof NameExpr name) {
            CallableDeclaration<?> owner = Ast.enclosingCallable(name);
            BoundArg bound = owner == null ? null : bound(name.getNameAsString(), owner, ctx);
            if (bound != null) {
                bound.expressions().forEach(e -> values.addAll(isCollectionLiteral(e)
                        ? elements(e, bound.ctx())
                        : strings(e, bound.ctx())));
                return values;
            }
            Optional<VariableDeclarator> local = owner == null ? Optional.empty() : localDeclarator(name, owner);
            if (local.isPresent() && local.get().getInitializer().isPresent()) {
                return elements(local.get().getInitializer().get(), ctx);
            }
        }
        if (iterable instanceof ArrayCreationExpr creation && creation.getInitializer().isPresent()) {
            return elements(creation.getInitializer().get(), ctx);
        }
        if (iterable instanceof ArrayInitializerExpr array) {
            array.getValues().forEach(e -> values.addAll(strings(e, ctx)));
            return values;
        }
        if (iterable instanceof MethodCallExpr call && isCollectionLiteral(call)) {
            call.getArguments().forEach(e -> values.addAll(strings(e, ctx)));
            return values;
        }
        return strings(iterable, ctx);
    }

    private static boolean isCollectionLiteral(Expression expr) {
        if (expr instanceof ArrayCreationExpr || expr instanceof ArrayInitializerExpr) {
            return true;
        }
        return expr instanceof MethodCallExpr call
                && Set.of("of", "asList").contains(call.getNameAsString())
                && call.getScope().map(s -> Set.of("List", "Set", "Arrays").contains(s.toString())).orElse(false);
    }

    private Set<String> fieldAccess(FieldAccessExpr field, Ctx ctx) {
        String id = field.getNameAsString();
        if (field.getScope().isThisExpr()) {
            return fieldValue(Ast.enclosingType(field), id, ctx).orElse(Set.of());
        }
        String typeName = Ast.simpleName(field.getScope().toString());
        return index.type(typeName).flatMap(type -> fieldValue(type, id, ctx)).orElse(Set.of());
    }

    private Set<String> call(MethodCallExpr call, Ctx ctx) {
        Optional<MethodDeclaration> target = sourceTarget(call);
        if (target.isPresent() && target.get().getBody().isPresent()) {
            MethodDeclaration method = target.get();
            Ctx callee = new Ctx(method, bind(method, call.getArguments(), ctx), ctx, call, ctx.depth() + 1);
            Set<String> values = new LinkedHashSet<>();
            returns(method).forEach(r -> r.getExpression().ifPresent(e -> values.addAll(strings(e, callee))));
            return values;
        }
        String name = call.getNameAsString();
        Optional<Expression> scope = call.getScope();
        if (Set.of("getName", "toString").contains(name) && scope.isPresent() && call.getArguments().isEmpty()) {
            return strings(scope.get(), ctx);
        }
        if (name.equals("valueOf") && scope.map(s -> s.toString().equals("String")).orElse(false)
                && call.getArguments().size() == 1) {
            return strings(call.getArgument(0), ctx);
        }
        if (NAME_FACTORIES.contains(name) && call.getArguments().isNonEmpty()
                && scope.map(Expression::isNameExpr).orElse(false)) {
            return strings(call.getArgument(0), ctx);
        }
        if (scope.map(Expression::isMethodCallExpr).orElse(false)) {
            // builder chains: QueueBuilder.durable(name).deadLetterExchange(x).build()
            return strings(scope.get(), ctx);
        }
        return Set.of();
    }

    private Optional<Set<String>> fieldValue(TypeDeclaration<?> type, String name, Ctx ctx) {
        for (TypeDeclaration<?> t = type; t != null; t = Ast.enclosingType(t)) {
            Optional<VariableDeclarator> variable = fieldDeclarator(t, name);
            if (variable.isPresent()) {
                return Optional.of(variable.get().getInitializer()
                        .map(init -> strings(init, ctx.detached()))
                        .orElse(Set.of()));
            }
        }
        return Optional.empty();
    }

    private Set<String> staticImport(NameExpr name, Ctx ctx) {
        CompilationUnit unit = name.findCompilationUnit().orElse(null);
        if (unit == null) {
            return Set.of();
        }
        String id = name.getNameAsString();
        for (ImportDeclaration imported : unit.getImports()) {
            if (!imported.isStatic()) {
                continue;
            }
            String typeName;
            if (imported.isAsterisk()) {
                typeName = imported.getName().getIdentifier();
            } else if (imported.getName().getIdentifier().equals(id)) {
                typeName = imported.getName().getQualifier().map(Name::getIdentifier).orElse(null);
            } else {
                continue;
            }
            Optional<Set<String>> value = index.type(typeName).flatMap(type -> fieldValue(type, id, ctx));
            if (value.isPresent()) {
                return value.get();
            }
        }
        return Set.of();
    }

    // ------------------------------------------------------------------ types

    /**
     * The most specific type name known for {@code expr}: the class instantiated, the declared
     * type otherwise.
     */
    Optional<String> typeOf(Expression expr, Ctx ctx) {
        if (ctx.depth() > MAX_DEPTH) {
            return Optional.empty();
        }
        if (expr instanceof ObjectCreationExpr creation) {
            return Optional.of(creation.getType().getNameAsString());
        }
        if (expr instanceof CastExpr cast) {
            return Optional.of(Ast.typeName(cast.getType()));
        }
        if (expr instanceof EnclosedExpr enclosed) {
            return typeOf(enclosed.getInner(), ctx);
        }
        if (expr instanceof StringLiteralExpr || expr instanceof TextBlockLiteralExpr) {
            return Optional.of("String");
        }
        if (expr instanceof MethodCallExpr call) {
            return sourceTarget(call).map(m -> Ast.typeName(m.getType())).filter(t -> !t.equals("void"));
        }
        if (expr instanceof NameExpr name) {
            String id = name.getNameAsString();
            CallableDeclaration<?> owner = Ast.enclosingCallable(name);
            if (owner != null) {
                Optional<VariableDeclarator> local = localDeclarator(name, owner);
                if (local.isPresent()) {
                    VariableDeclarator variable = local.get();
                    Optional<String> fromInitializer = variable.getInitializer().flatMap(init -> typeOf(init, ctx));
                    return fromInitializer.isPresent() || variable.getType().isVarType()
                            ? fromInitializer
                            : Optional.of(Ast.typeName(variable.getType()));
                }
                BoundArg bound = bound(id, owner, ctx);
                if (bound != null) {
                    for (Expression e : bound.expressions()) {
                        Optional<String> type = typeOf(e, bound.ctx());
                        if (type.isPresent()) {
                            return type;
                        }
                    }
                }
                Optional<Parameter> param = owner.getParameterByName(id);
                if (param.isPresent()) {
                    return Optional.of(Ast.typeName(param.get().getType()));
                }
            }
            return fieldType(Ast.enclosingType(name), id);
        }
        return Optional.empty();
    }

    /**
     * Declared type of the object a method is called on ({@code rabbitTemplate.convertAndSend}
     * → {@code RabbitTemplate}).
     */
    Optional<String> receiverType(MethodCallExpr call) {
        Expression scope = call.getScope().orElse(null);
        if (scope instanceof NameExpr name) {
            return declaredType(name);
        }
        if (scope instanceof FieldAccessExpr field && field.getScope().isThisExpr()) {
            return fieldType(Ast.enclosingType(field), field.getNameAsString());
        }
        return Optional.empty();
    }

    Optional<String> declaredType(NameExpr name) {
        String id = name.getNameAsString();
        Optional<String> lambdaParam = lambdaParameterType(name);
        if (lambdaParam.isPresent()) {
            return lambdaParam;
        }
        CallableDeclaration<?> owner = Ast.enclosingCallable(name);
        if (owner != null) {
            Optional<VariableDeclarator> local = localDeclarator(name, owner);
            if (local.isPresent()) {
                return Optional.of(Ast.typeName(local.get().getType()));
            }
            Optional<Parameter> param = owner.getParameterByName(id);
            if (param.isPresent()) {
                return Optional.of(Ast.typeName(param.get().getType()));
            }
        }
        return fieldType(Ast.enclosingType(name), id);
    }

    /**
     * Type of a lambda parameter {@code name} refers to: the declared one, or for an implicitly
     * typed callback the type the receiving API passes, e.g.
     * {@code rabbitTemplate.invoke(operations -> operations.convertAndSend(...))}.
     */
    private Optional<String> lambdaParameterType(NameExpr name) {
        for (Node node = name.getParentNode().orElse(null); node != null && !(node instanceof CallableDeclaration<?>);
             node = node.getParentNode().orElse(null)) {
            if (!(node instanceof LambdaExpr lambda)) {
                continue;
            }
            Optional<Parameter> param = lambda.getParameters().stream()
                    .filter(p -> p.getNameAsString().equals(name.getNameAsString()))
                    .findFirst();
            if (param.isEmpty()) {
                continue;
            }
            if (!param.get().getType().isUnknownType()) {
                return Optional.of(Ast.typeName(param.get().getType()));
            }
            return lambda.getParentNode()
                    .filter(MethodCallExpr.class::isInstance)
                    .map(MethodCallExpr.class::cast)
                    .flatMap(call -> receiverType(call)
                            .map(receiver -> CALLBACK_PARAMETER_TYPES.get(receiver + "#" + call.getNameAsString())));
        }
        return Optional.empty();
    }

    Optional<String> fieldType(TypeDeclaration<?> type, String name) {
        for (TypeDeclaration<?> t = type; t != null; t = Ast.enclosingType(t)) {
            Optional<VariableDeclarator> variable = fieldDeclarator(t, name);
            if (variable.isPresent()) {
                return Optional.of(Ast.typeName(variable.get().getType()));
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ lookup helpers

    /**
     * The source method a call invokes: a method of the enclosing class (or an outer class), a
     * static method of an indexed type, or a method of an indexed type held in a field.
     */
    Optional<MethodDeclaration> sourceTarget(MethodCallExpr call) {
        Expression scope = call.getScope().orElse(null);
        List<TypeDeclaration<?>> candidates = new ArrayList<>();
        if (scope == null || scope.isThisExpr()) {
            for (TypeDeclaration<?> t = Ast.enclosingType(call); t != null; t = Ast.enclosingType(t)) {
                candidates.add(t);
            }
        } else if (scope instanceof NameExpr name) {
            String typeName = declaredType(name).orElse(name.getNameAsString());
            index.type(typeName).ifPresent(candidates::add);
        }
        for (TypeDeclaration<?> type : candidates) {
            for (MethodDeclaration method : type.getMethodsByName(call.getNameAsString())) {
                if (accepts(method, call.getArguments().size())) {
                    return Optional.of(method);
                }
            }
        }
        return Optional.empty();
    }

    private static List<ReturnStmt> returns(MethodDeclaration method) {
        return method.findAll(ReturnStmt.class).stream()
                .filter(r -> Ast.enclosingCallable(r) == method)
                .filter(r -> r.findAncestor(LambdaExpr.class).filter(method::isAncestorOf).isEmpty())
                .toList();
    }

    private static BoundArg bound(String id, CallableDeclaration<?> owner, Ctx ctx) {
        return ctx.method() == owner ? ctx.bindings().get(id) : null;
    }

    /**
     * The latest declaration of a local variable named like {@code name} that precedes it.
     */
    private static Optional<VariableDeclarator> localDeclarator(NameExpr name, CallableDeclaration<?> owner) {
        Position at = name.getBegin().orElse(null);
        if (at == null) {
            return Optional.empty();
        }
        return owner.findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(name.getNameAsString()))
                .filter(v -> Ast.enclosingCallable(v) == owner)
                .filter(v -> v.getBegin().map(begin -> begin.isBefore(at)).orElse(false))
                .reduce((first, second) -> second);
    }

    private static Optional<ForEachStmt> forEachOf(VariableDeclarator variable) {
        return variable.getParentNode()
                .flatMap(declaration -> declaration.getParentNode()
                        .filter(ForEachStmt.class::isInstance)
                        .map(ForEachStmt.class::cast)
                        .filter(loop -> loop.getVariable() == declaration));
    }

    private static Optional<VariableDeclarator> fieldDeclarator(TypeDeclaration<?> type, String name) {
        return type.getFieldByName(name)
                .flatMap(field -> field.getVariables().stream()
                        .filter(v -> v.getNameAsString().equals(name))
                        .findFirst());
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return values;
    }

    private static Set<String> concat(Set<String> left, Set<String> right) {
        Set<String> values = new LinkedHashSet<>();
        for (String l : left) {
            for (String r : right) {
                if (values.size() >= MAX_VALUES) {
                    return values;
                }
                values.add(l + r);
            }
        }
        return values;
    }
}

package ru.itmo.vkr.channels;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import ru.itmo.vkr.channels.model.CodeRef;
import ru.itmo.vkr.channels.model.Endpoint;
import ru.itmo.vkr.channels.model.HttpClientMethod;
import ru.itmo.vkr.channels.model.Param;
import ru.itmo.vkr.channels.model.Unresolved;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spring MVC handler methods and OpenFeign client methods of one service. Programmatic HTTP
 * clients ({@code RestTemplate}, {@code RestClient}, {@code WebClient}) are listed as unresolved:
 * their target is only known at runtime.
 */
final class SpringWebExtractor {

    record Result(List<Endpoint> endpoints, List<HttpClientMethod> clients, List<Unresolved> unresolved) {
    }

    private record Mapping(List<String> httpMethods, List<String> paths) {
    }

    private static final Map<String, String> SHORTCUT_MAPPINGS = Map.of(
            "GetMapping", "GET", "PostMapping", "POST", "PutMapping", "PUT",
            "DeleteMapping", "DELETE", "PatchMapping", "PATCH");
    private static final Map<String, String> PARAM_SOURCES = Map.of(
            "PathVariable", "PATH", "RequestParam", "QUERY", "RequestBody", "BODY", "RequestHeader", "HEADER",
            "CookieValue", "COOKIE", "ModelAttribute", "QUERY", "RequestPart", "BODY",
            "AuthenticationPrincipal", "PRINCIPAL");
    private static final Set<String> FRAMEWORK_TYPES = Set.of(
            "Authentication", "Principal", "Jwt", "JwtAuthenticationToken", "HttpServletRequest",
            "HttpServletResponse", "ServerHttpRequest", "ServerHttpResponse", "ServerWebExchange", "HttpSession",
            "Locale", "BindingResult", "Model", "UriComponentsBuilder");
    private static final Set<String> QUERY_TYPES = Set.of("Pageable", "Sort");
    private static final Set<String> HTTP_CLIENT_TYPES = Set.of("RestTemplate", "RestClient", "WebClient");
    private static final String UNRESOLVED_SEGMENT = "{unresolved}";

    private final Path root;
    private final ValueResolver resolver;
    private final GatewayRoutes gateway;

    SpringWebExtractor(Path root, ValueResolver resolver, GatewayRoutes gateway) {
        this.root = root;
        this.resolver = resolver;
        this.gateway = gateway;
    }

    Result extract(SourceIndex.Module module) {
        List<Endpoint> endpoints = new ArrayList<>();
        List<HttpClientMethod> clients = new ArrayList<>();
        List<Unresolved> unresolved = new ArrayList<>();
        for (CompilationUnit unit : module.units()) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (Ast.hasAnnotation(type, "RestController") || Ast.hasAnnotation(type, "Controller")) {
                    endpoints(module, type, endpoints);
                }
                if (type.isInterface()) {
                    Ast.annotation(type, "FeignClient")
                            .ifPresent(feign -> feignClient(module, type, feign, clients));
                }
            }
            programmaticHttpCalls(module, unit, unresolved);
        }
        return new Result(endpoints, clients, unresolved);
    }

    private void endpoints(SourceIndex.Module module, ClassOrInterfaceDeclaration controller, List<Endpoint> out) {
        List<String> prefixes = mapping(controller).map(Mapping::paths).orElse(List.of(""));
        for (MethodDeclaration handler : controller.getMethods()) {
            Optional<Mapping> mapping = mapping(handler);
            if (mapping.isEmpty()) {
                continue;
            }
            List<Param> params = params(handler);
            for (String prefix : prefixes) {
                for (String path : mapping.get().paths()) {
                    String fullPath = Patterns.joinPaths(prefix, path);
                    for (String httpMethod : mapping.get().httpMethods()) {
                        out.add(new Endpoint("ep:" + module.name() + ":" + httpMethod + " " + fullPath,
                                module.name(), httpMethod, fullPath, params,
                                gateway.exposes(module.name(), fullPath), Ast.ref(handler, root)));
                    }
                }
            }
        }
    }

    private void feignClient(SourceIndex.Module module, ClassOrInterfaceDeclaration client, AnnotationExpr feign,
                             List<HttpClientMethod> out) {
        String target = first(Ast.attribute(feign, "name", "value", "serviceId"));
        String url = first(Ast.attribute(feign, "url"));
        String basePath = first(Ast.attribute(feign, "path"));
        List<String> prefixes = mapping(client).map(Mapping::paths).orElse(List.of(""));
        for (MethodDeclaration method : client.getMethods()) {
            Optional<Mapping> mapping = mapping(method);
            if (mapping.isEmpty()) {
                continue;
            }
            List<Param> params = params(method);
            List<CodeRef> callSites = callSites(module, client.getNameAsString(), method.getNameAsString());
            for (String prefix : prefixes) {
                for (String path : mapping.get().paths()) {
                    String fullPath = Patterns.joinPaths(basePath, prefix, path);
                    for (String httpMethod : mapping.get().httpMethods()) {
                        String id = "http:" + module.name() + ":" + client.getNameAsString() + "#"
                                + method.getNameAsString() + ":" + httpMethod + " " + fullPath;
                        out.add(new HttpClientMethod(id, module.name(), target, url, httpMethod, fullPath, params,
                                Ast.ref(method, root), callSites));
                    }
                }
            }
        }
    }

    /**
     * Calls of a client method through a field, parameter or local variable of the client type.
     */
    private List<CodeRef> callSites(SourceIndex.Module module, String clientType, String methodName) {
        List<CodeRef> sites = new ArrayList<>();
        for (CompilationUnit unit : module.units()) {
            for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                if (call.getNameAsString().equals(methodName)
                        && resolver.receiverType(call).filter(clientType::equals).isPresent()) {
                    sites.add(Ast.ref(call, root));
                }
            }
        }
        return sites;
    }

    private void programmaticHttpCalls(SourceIndex.Module module, CompilationUnit unit, List<Unresolved> out) {
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            boolean outermostOfChain = call.getParentNode()
                    .filter(parent -> parent instanceof MethodCallExpr outer
                            && outer.getScope().filter(scope -> scope == call).isPresent())
                    .isEmpty();
            if (!outermostOfChain) {
                continue;
            }
            Expression receiver = call;
            while (receiver instanceof MethodCallExpr chained && chained.getScope().isPresent()) {
                receiver = chained.getScope().get();
            }
            Optional<String> type = Optional.empty();
            if (receiver instanceof NameExpr name) {
                type = resolver.declaredType(name);
            } else if (receiver instanceof FieldAccessExpr field && field.getScope().isThisExpr()) {
                type = resolver.fieldType(Ast.enclosingType(field), field.getNameAsString());
            }
            type.filter(HTTP_CLIENT_TYPES::contains).ifPresent(client -> out.add(new Unresolved(module.name(),
                    "HTTP_CALL", client + " call: target URL is built at runtime and not modelled",
                    Ast.ref(call, root))));
        }
    }

    private Optional<Mapping> mapping(NodeWithAnnotations<?> node) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String name = Ast.simpleName(annotation.getNameAsString());
            String shortcut = SHORTCUT_MAPPINGS.get(name);
            if (shortcut != null) {
                return Optional.of(new Mapping(List.of(shortcut), paths(annotation)));
            }
            if (name.equals("RequestMapping")) {
                List<String> methods = Ast.attribute(annotation, "method").stream()
                        .map(e -> Ast.simpleName(e.toString()))
                        .toList();
                return Optional.of(new Mapping(methods.isEmpty() ? List.of("ANY") : methods, paths(annotation)));
            }
        }
        return Optional.empty();
    }

    private List<String> paths(AnnotationExpr annotation) {
        List<Expression> expressions = Ast.attribute(annotation, "value", "path");
        if (expressions.isEmpty()) {
            return List.of("");
        }
        Set<String> paths = new LinkedHashSet<>();
        for (Expression expression : expressions) {
            Set<String> values = resolver.strings(expression, ValueResolver.Ctx.root(null));
            paths.addAll(values.isEmpty() ? Set.of(UNRESOLVED_SEGMENT) : values);
        }
        return List.copyOf(paths);
    }

    private List<Param> params(CallableDeclaration<?> method) {
        List<Param> params = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            String type = Ast.typeName(parameter.getType());
            String name = parameter.getNameAsString();
            String source = null;
            for (AnnotationExpr annotation : parameter.getAnnotations()) {
                source = PARAM_SOURCES.get(Ast.simpleName(annotation.getNameAsString()));
                if (source != null) {
                    String explicit = first(Ast.attribute(annotation, "value", "name"));
                    if (explicit != null && !explicit.isBlank()) {
                        name = explicit;
                    }
                    break;
                }
            }
            if (source == null) {
                source = FRAMEWORK_TYPES.contains(type) ? "FRAMEWORK" : QUERY_TYPES.contains(type) ? "QUERY" : "IMPLICIT";
            }
            params.add(new Param(name, type, source));
        }
        return List.copyOf(params);
    }

    private String first(List<Expression> expressions) {
        for (Expression expression : expressions) {
            Set<String> values = resolver.strings(expression, ValueResolver.Ctx.root(null));
            if (!values.isEmpty()) {
                return values.iterator().next();
            }
        }
        return null;
    }
}

package ru.itmo.vkr.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Spring Cloud Gateway routes to discovered services ({@code uri: lb://name}) and their
 * {@code Path} predicates: they decide which endpoints are reachable from outside the system.
 * <p>
 * Path rewriting filters ({@code StripPrefix}, {@code RewritePath}) are not modelled.
 */
final class GatewayRoutes {

    record Route(String id, String service, List<String> pathPatterns) {
    }

    private final List<Route> routes;

    private GatewayRoutes(List<Route> routes) {
        this.routes = routes;
    }

    static GatewayRoutes none() {
        return new GatewayRoutes(null);
    }

    static GatewayRoutes load(Path file) throws IOException {
        List<Route> routes = new ArrayList<>();
        collect(new YAMLMapper().readTree(file.toFile()), routes);
        return new GatewayRoutes(List.copyOf(routes));
    }

    /**
     * Whether a request to {@code path} of {@code service} is routed by the gateway; {@code null}
     * when no gateway configuration is known.
     */
    Boolean exposes(String service, String path) {
        if (routes == null) {
            return null;
        }
        return routes.stream()
                .filter(route -> route.service().equals(service))
                .flatMap(route -> route.pathPatterns().stream())
                .map(Patterns::antPattern)
                .map(Pattern::asMatchPredicate)
                .anyMatch(matches -> matches.test(path));
    }

    private static void collect(JsonNode node, List<Route> out) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (entry.getKey().equals("routes") && entry.getValue().isArray()) {
                    entry.getValue().forEach(route -> route(route).ifPresent(out::add));
                } else {
                    collect(entry.getValue(), out);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, out));
        }
    }

    private static Optional<Route> route(JsonNode route) {
        String uri = route.path("uri").asText("");
        if (!uri.startsWith("lb://")) {
            return Optional.empty();
        }
        String service = uri.substring("lb://".length()).replaceAll("/+$", "");
        List<String> patterns = new ArrayList<>();
        for (JsonNode predicate : route.path("predicates")) {
            if (predicate.isTextual()) {
                String text = predicate.asText();
                int eq = text.indexOf('=');
                if (eq > 0 && text.substring(0, eq).trim().equals("Path")) {
                    addPatterns(text.substring(eq + 1), patterns);
                }
            } else if (predicate.path("name").asText().equals("Path")) {
                for (JsonNode arg : predicate.path("args")) {
                    if (arg.isTextual()) {
                        addPatterns(arg.asText(), patterns);
                    } else {
                        arg.forEach(value -> addPatterns(value.asText(), patterns));
                    }
                }
            }
        }
        return Optional.of(new Route(route.path("id").asText(service), service, List.copyOf(patterns)));
    }

    private static void addPatterns(String commaSeparated, List<String> out) {
        for (String pattern : commaSeparated.split(",")) {
            if (!pattern.isBlank()) {
                out.add(pattern.trim());
            }
        }
    }
}

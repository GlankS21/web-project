package ru.itmo.vkr.channels.model;

import java.util.List;

/**
 * A method of a declarative HTTP client (OpenFeign) together with the places that call it.
 * {@code url} is set when the client targets a fixed address instead of a discovered service.
 */
public record HttpClientMethod(String id, String service, String targetService, String url, String httpMethod,
                               String path, List<Param> params, CodeRef declaration, List<CodeRef> callSites) {
}

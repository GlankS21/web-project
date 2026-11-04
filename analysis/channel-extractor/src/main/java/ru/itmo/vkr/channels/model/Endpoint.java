package ru.itmo.vkr.channels.model;

import java.util.List;

/**
 * An HTTP handler method. {@code exposedViaGateway} is {@code null} when no gateway configuration
 * was available.
 */
public record Endpoint(String id, String service, String httpMethod, String path, List<Param> params,
                       Boolean exposedViaGateway, CodeRef handler) {
}

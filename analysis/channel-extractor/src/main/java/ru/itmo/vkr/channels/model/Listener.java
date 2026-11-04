package ru.itmo.vkr.channels.model;

/**
 * A message handler consuming a queue. {@code dispatch} is {@code METHOD} for a method annotated
 * with {@code @RabbitListener} or {@code TYPE} for a {@code @RabbitHandler} chosen by payload type.
 */
public record Listener(String id, String service, String queue, String dispatch, String payloadType,
                       String payloadParam, CodeRef handler) {
}

package ru.itmo.vkr.channels.model;

/**
 * Something the extractor saw but could not turn into a channel, kept so that nothing is
 * silently dropped from the model.
 */
public record Unresolved(String service, String kind, String detail, CodeRef site) {
}

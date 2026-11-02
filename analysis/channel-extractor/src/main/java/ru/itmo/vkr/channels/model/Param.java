package ru.itmo.vkr.channels.model;

/**
 * A handler or client parameter and where its value comes from in the HTTP request
 * ({@code PATH}, {@code QUERY}, {@code BODY}, {@code HEADER}, {@code COOKIE}, {@code PRINCIPAL},
 * {@code FRAMEWORK} or {@code IMPLICIT} for unannotated parameters bound by Spring).
 */
public record Param(String name, String type, String source) {
}

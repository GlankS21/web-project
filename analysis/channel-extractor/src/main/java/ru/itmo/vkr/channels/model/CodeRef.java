package ru.itmo.vkr.channels.model;

/**
 * A location in the analysed sources. {@code className} is fully qualified so that a bytecode-level
 * taint engine can map the reference back to its own method signatures.
 */
public record CodeRef(String file, String className, String method, int line) {

    /**
     * Short human readable form, e.g. {@code OrderService#create:42}.
     */
    public String label() {
        String simple = className == null ? "?" : className.substring(className.lastIndexOf('.') + 1);
        return simple + (method == null ? "" : "#" + method) + ":" + line;
    }
}

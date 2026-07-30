package com.pauluno.finledger.infrastructure.audit;

import java.util.Optional;

/**
 * Request-scoped W3C trace context parsed from {@code traceparent} (plan §10).
 */
public final class TraceContext {

    private static final ThreadLocal<Parsed> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId, String spanId) {
        CURRENT.set(new Parsed(traceId, spanId));
    }

    public static Optional<Parsed> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Parsed(String traceId, String spanId) {
    }
}

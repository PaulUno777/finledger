package com.pauluno.finledger.infrastructure.audit;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manual W3C Trace Context {@code traceparent} parser.
 * Primary correlation uses Micrometer Tracing / OpenTelemetry (FL-150);
 * this remains as a fallback when no current span is available.
 */
public final class TraceparentParser {

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$",
            Pattern.CASE_INSENSITIVE);

    private TraceparentParser() {
    }

    public static Optional<TraceContext.Parsed> parse(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TRACEPARENT.matcher(header.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new TraceContext.Parsed(
                matcher.group(2).toLowerCase(),
                matcher.group(3).toLowerCase()
        ));
    }
}

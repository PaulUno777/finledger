package com.pauluno.finledger.sdkref;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W3C Trace Context {@code traceparent} generate / validate / propagate helpers.
 */
public final class Traceparent {

    public static final String HEADER = "traceparent";

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$",
            Pattern.CASE_INSENSITIVE);

    private static final SecureRandom RANDOM = new SecureRandom();

    private Traceparent() {
    }

    /** New root span: {@code 00-<trace-id>-<span-id>-01}. */
    public static String generate() {
        return format("00", randomHex(16), randomHex(8), "01");
    }

    /**
     * Continues a parent {@code traceparent} with a new span-id (same trace-id),
     * or generates a fresh root if parent is missing/invalid.
     */
    public static String continueOrGenerate(String parentHeader) {
        Optional<Parsed> parent = parse(parentHeader);
        if (parent.isEmpty()) {
            return generate();
        }
        Parsed p = parent.get();
        return format(p.version(), p.traceId(), randomHex(8), p.flags());
    }

    public static Optional<Parsed> parse(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TRACEPARENT.matcher(header.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(
                matcher.group(1).toLowerCase(Locale.ROOT),
                matcher.group(2).toLowerCase(Locale.ROOT),
                matcher.group(3).toLowerCase(Locale.ROOT),
                matcher.group(4).toLowerCase(Locale.ROOT)));
    }

    public record Parsed(String version, String traceId, String spanId, String flags) {
    }

    private static String format(String version, String traceId, String spanId, String flags) {
        return version + "-" + traceId + "-" + spanId + "-" + flags;
    }

    private static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Ensures non-blank header value for outbound calls. */
    public static String requireValidOrGenerate(String maybe) {
        return parse(maybe).map(p -> format(p.version(), p.traceId(), p.spanId(), p.flags()))
                .orElseGet(Traceparent::generate);
    }
}

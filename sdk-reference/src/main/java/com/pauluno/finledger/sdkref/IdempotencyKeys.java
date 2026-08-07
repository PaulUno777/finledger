package com.pauluno.finledger.sdkref;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotency-Key helpers for FinLedger mutating calls.
 * <p>
 * Server rule: same key + same body → replay; same key + different body → {@code 409}.
 */
public final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    /** Fresh opaque key (UUID). Prefer one key per intended business effect. */
    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** SHA-256 hex of UTF-8 body bytes — use to decide whether a key may be reused. */
    public static String bodyFingerprint(String body) {
        Objects.requireNonNull(body, "body");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /**
     * Returns {@code true} only when the stored fingerprint matches the candidate body
     * (safe to reuse the same Idempotency-Key).
     */
    public static boolean sameBodyAsStored(String storedFingerprint, String candidateBody) {
        if (storedFingerprint == null || storedFingerprint.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                storedFingerprint.getBytes(StandardCharsets.UTF_8),
                bodyFingerprint(candidateBody).getBytes(StandardCharsets.UTF_8));
    }
}

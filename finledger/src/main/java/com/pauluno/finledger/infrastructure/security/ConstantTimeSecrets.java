package com.pauluno.finledger.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Constant-time secret comparison via SHA-256 digests (FL-158).
 */
public final class ConstantTimeSecrets {

    private ConstantTimeSecrets() {
    }

    public static boolean equals(String expected, String provided) {
        byte[] a = sha256(expected == null ? "" : expected);
        byte[] b = sha256(provided == null ? "" : provided);
        return MessageDigest.isEqual(a, b);
    }

    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

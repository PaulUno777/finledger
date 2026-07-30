package com.pauluno.finledger.application.rail;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Inbound webhook HMAC: {@code HMAC-SHA256(timestamp + "." + nonce + "." + body)}.
 */
public final class RailWebhookHmac {

    public static final String SECRET_KEY = "FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET";

    private RailWebhookHmac() {
    }

    public static String sign(String secret, String timestamp, String nonce, String body) {
        String payload = timestamp + "." + nonce + "." + body;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256", ex);
        }
    }

    public static boolean matches(
            String secret,
            String timestamp,
            String nonce,
            String body,
            String providedHex
    ) {
        if (secret == null || secret.isBlank() || providedHex == null || providedHex.isBlank()) {
            return false;
        }
        String expected = sign(secret, timestamp, nonce, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedHex.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }
}

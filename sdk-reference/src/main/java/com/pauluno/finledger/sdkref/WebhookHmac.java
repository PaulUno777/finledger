package com.pauluno.finledger.sdkref;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Rail settlement webhook HMAC — parity with server {@code RailWebhookHmac}:
 * {@code HMAC-SHA256(timestamp + "." + nonce + "." + body)} as lowercase hex.
 * <p>
 * Headers: {@code X-Finledger-Timestamp}, {@code X-Finledger-Nonce}, {@code X-Finledger-Signature}.
 */
public final class WebhookHmac {

    public static final String HEADER_TIMESTAMP = "X-Finledger-Timestamp";
    public static final String HEADER_NONCE = "X-Finledger-Nonce";
    public static final String HEADER_SIGNATURE = "X-Finledger-Signature";

    private WebhookHmac() {
    }

    public static String sign(String secret, String timestamp, String nonce, String body) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(body, "body");
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
                providedHex.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}

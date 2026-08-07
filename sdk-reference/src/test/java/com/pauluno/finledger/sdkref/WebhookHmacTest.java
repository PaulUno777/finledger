package com.pauluno.finledger.sdkref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class WebhookHmacTest {

    @Test
    void sign_and_matches_known_vector() {
        String secret = "contract-rail-hmac-secret";
        String timestamp = "1710000000";
        String nonce = "n-1";
        String body = "{\"railReference\":\"r1\"}";
        String sig = WebhookHmac.sign(secret, timestamp, nonce, body);
        assertTrue(WebhookHmac.matches(secret, timestamp, nonce, body, sig));
        assertTrue(WebhookHmac.matches(secret, timestamp, nonce, body, sig.toUpperCase()));
        assertFalse(WebhookHmac.matches(secret, timestamp, nonce, body, "deadbeef"));
        assertEquals(64, sig.length());
    }
}

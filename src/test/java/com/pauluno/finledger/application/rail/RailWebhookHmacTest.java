package com.pauluno.finledger.application.rail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class RailWebhookHmacTest {

    @Test
    void should_accept_valid_signature() {
        String secret = "test-secret";
        String timestamp = "1710000000";
        String nonce = "n-1";
        String body = "{\"railReference\":\"manual-1\"}";
        String sig = RailWebhookHmac.sign(secret, timestamp, nonce, body);

        assertThat(RailWebhookHmac.matches(secret, timestamp, nonce, body, sig)).isTrue();
    }

    @Test
    void should_reject_tampered_body() {
        String secret = "test-secret";
        String sig = RailWebhookHmac.sign(secret, "1", "n", "{\"a\":1}");
        assertThat(RailWebhookHmac.matches(secret, "1", "n", "{\"a\":2}", sig)).isFalse();
    }
}

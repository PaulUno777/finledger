package com.pauluno.finledger.sdkref;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class RetryPolicyTest {

    @Test
    void retries_5xx_and_connection_not_generic_4xx() {
        AtomicInteger sleeps = new AtomicInteger();
        RetryPolicy policy = new RetryPolicy(3, 1L, 10L, true, millis -> sleeps.incrementAndGet());

        assertTrue(policy.shouldRetry(0, 503, false));
        assertTrue(policy.shouldRetry(0, 429, false));
        assertTrue(policy.shouldRetry(0, 408, false));
        assertTrue(policy.shouldRetry(0, 0, true));
        assertFalse(policy.shouldRetry(0, 401, false));
        assertFalse(policy.shouldRetry(0, 409, false));
        assertFalse(policy.shouldRetry(2, 503, false));

        policy.backoff(0);
        assertTrue(sleeps.get() >= 1);
    }

    @Test
    void optional_408_429_can_be_disabled() {
        RetryPolicy policy = new RetryPolicy(3, 1L, 10L, false, millis -> {
        });
        assertFalse(policy.shouldRetry(0, 429, false));
        assertTrue(policy.shouldRetry(0, 500, false));
    }
}

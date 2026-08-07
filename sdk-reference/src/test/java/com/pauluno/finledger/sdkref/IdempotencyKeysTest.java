package com.pauluno.finledger.sdkref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class IdempotencyKeysTest {

    @Test
    void newKey_isUuidShaped() {
        String key = IdempotencyKeys.newKey();
        assertEquals(36, key.length());
        assertNotEquals(IdempotencyKeys.newKey(), key);
    }

    @Test
    void sameBodyAsStored_requiresMatchingFingerprint() {
        String body = "{\"a\":1}";
        String fp = IdempotencyKeys.bodyFingerprint(body);
        assertTrue(IdempotencyKeys.sameBodyAsStored(fp, body));
        assertFalse(IdempotencyKeys.sameBodyAsStored(fp, "{\"a\":2}"));
        assertFalse(IdempotencyKeys.sameBodyAsStored(null, body));
    }
}

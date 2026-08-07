package com.pauluno.finledger.sdkref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TraceparentTest {

    @Test
    void generate_matches_w3c_shape() {
        String tp = Traceparent.generate();
        assertTrue(Traceparent.parse(tp).isPresent());
        assertEquals(55, tp.length());
    }

    @Test
    void continueOrGenerate_keeps_trace_id() {
        String parent = Traceparent.generate();
        String child = Traceparent.continueOrGenerate(parent);
        assertEquals(
                Traceparent.parse(parent).orElseThrow().traceId(),
                Traceparent.parse(child).orElseThrow().traceId());
        assertNotEquals(
                Traceparent.parse(parent).orElseThrow().spanId(),
                Traceparent.parse(child).orElseThrow().spanId());
    }

    @Test
    void continueOrGenerate_invalid_parent_creates_root() {
        assertTrue(Traceparent.parse(Traceparent.continueOrGenerate("not-a-trace")).isPresent());
    }
}

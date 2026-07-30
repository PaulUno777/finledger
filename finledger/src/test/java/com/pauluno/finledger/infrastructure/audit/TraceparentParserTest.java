package com.pauluno.finledger.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TraceparentParserTest {

    @Test
    void should_parse_valid_traceparent() {
        var parsed = TraceparentParser.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(parsed).isPresent();
        assertThat(parsed.get().traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(parsed.get().spanId()).isEqualTo("00f067aa0ba902b7");
    }

    @Test
    void should_reject_blank_or_invalid() {
        assertThat(TraceparentParser.parse(null)).isEmpty();
        assertThat(TraceparentParser.parse("")).isEmpty();
        assertThat(TraceparentParser.parse("not-a-traceparent")).isEmpty();
    }
}

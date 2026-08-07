package com.pauluno.finledger.cli.ops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReadyCommandTest {

    @Test
    void isUp_detects_spring_health_json() {
        assertTrue(ReadyCommand.isUp("{\"status\":\"UP\"}"));
        assertTrue(ReadyCommand.isUp("{ \"status\" : \"UP\", \"components\": {} }"));
        assertFalse(ReadyCommand.isUp("{\"status\":\"DOWN\"}"));
        assertFalse(ReadyCommand.isUp(null));
    }
}

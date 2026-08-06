package com.pauluno.finledger.presentation.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Tag("unit")
class GlobalExceptionHandlerTest {

    @Test
    void should_map_no_resource_found_to_404_not_500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        NoResourceFoundException ex = new NoResourceFoundException(
                HttpMethod.GET, "/actuator/health", "actuator/health");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, request);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("NOT_FOUND", response.getBody().code());
        assertEquals("/actuator/health", response.getBody().path());
    }
}

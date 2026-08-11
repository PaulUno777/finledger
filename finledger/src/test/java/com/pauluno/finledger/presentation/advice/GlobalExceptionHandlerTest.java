package com.pauluno.finledger.presentation.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
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

    @Test
    void should_map_unreadable_body_to_400_invalid_argument() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Required request body is missing",
                new MockHttpInputMessage(new byte[0]));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenants/t/accounts");

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex, request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().code());
    }
}

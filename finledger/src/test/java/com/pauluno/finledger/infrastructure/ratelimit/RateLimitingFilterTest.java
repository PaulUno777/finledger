package com.pauluno.finledger.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class RateLimitingFilterTest {

    @Test
    void should_return_429_json_without_spring_object_mapper_bean() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCapacity(1);
        properties.setRefillPerSecond(1);
        RateLimitingFilter filter = new RateLimitingFilter(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenants");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(request, first, new MockFilterChain());
        filter.doFilter(request, second, new MockFilterChain());

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getContentAsString()).contains("RATE_LIMITED");
        assertThat(second.getContentType()).contains("application/json");
    }

    @Test
    void should_skip_non_api_paths() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCapacity(1);
        RateLimitingFilter filter = new RateLimitingFilter(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}

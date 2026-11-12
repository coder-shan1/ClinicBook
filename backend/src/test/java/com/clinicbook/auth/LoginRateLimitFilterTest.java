package com.clinicbook.auth;

import com.clinicbook.common.error.ApiErrorWriter;
import com.clinicbook.config.RateLimitProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final LoginRateLimiter limiter = new LoginRateLimiter(
            new RateLimitProperties(new RateLimitProperties.Login(1, Duration.ofMinutes(1))),
            Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));
    private final LoginRateLimitFilter filter = new LoginRateLimitFilter(limiter, new ApiErrorWriter(objectMapper));

    @Test
    void passesTheFirstLoginAttemptThroughTheChain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(loginRequest("10.0.0.1"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsLoginAttemptsOverTheLimit() throws Exception {
        filter.doFilter(loginRequest("10.0.0.1"), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(loginRequest("10.0.0.1"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("error").asText()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/auth/login");
    }

    @Test
    void doesNotLimitOtherEndpoints() throws Exception {
        filter.doFilter(loginRequest("10.0.0.1"), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setRemoteAddr("10.0.0.1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void tracksClientsIndependently() throws Exception {
        filter.doFilter(loginRequest("10.0.0.1"), new MockHttpServletResponse(), new MockFilterChain());

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(loginRequest("10.0.0.2"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest loginRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}

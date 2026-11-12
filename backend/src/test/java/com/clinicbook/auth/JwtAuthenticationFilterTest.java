package com.clinicbook.auth;

import com.clinicbook.user.Role;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAValidBearerToken() throws Exception {
        JwtPrincipal principal = new JwtPrincipal(42L, "jane@example.com", Role.DOCTOR, "Jane Doe");
        when(jwtService.parseAccessToken("token")).thenReturn(principal);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(principal);
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_DOCTOR");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void treatsAnInvalidTokenAsAnonymous() throws Exception {
        when(jwtService.parseAccessToken("bad")).thenThrow(new JwtException("bad token"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void ignoresRequestsWithoutABearerHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/auth/me"),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doesNotOverrideAnExistingAuthentication() throws Exception {
        JwtPrincipal principal = new JwtPrincipal(42L, "jane@example.com", Role.PATIENT, "Jane Doe");
        when(jwtService.parseAccessToken("token")).thenReturn(principal);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockFilterChain chain = new MockFilterChain();
        Authentication existing = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existing);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}

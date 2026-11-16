package com.clinicbook.auth;

import com.clinicbook.common.error.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final LoginRateLimiter rateLimiter;
    private final ApiErrorWriter errorWriter;

    public LoginRateLimitFilter(LoginRateLimiter rateLimiter, ApiErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isLoginRequest(request) || rateLimiter.tryAcquire(clientKey(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(rateLimiter.windowSeconds()));
        errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "Too many login attempts. Try again later.", request.getRequestURI());
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return LOGIN_PATH.equals(path);
    }

    private String clientKey(HttpServletRequest request) {
        // Use the socket address: forwarding headers are only trustworthy behind a known proxy.
        return request.getRemoteAddr();
    }
}

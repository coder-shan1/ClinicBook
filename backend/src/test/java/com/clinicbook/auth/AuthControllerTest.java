package com.clinicbook.auth;

import com.clinicbook.auth.dto.AuthResponse;
import com.clinicbook.auth.dto.LoginRequest;
import com.clinicbook.auth.dto.RefreshRequest;
import com.clinicbook.auth.dto.RegisterRequest;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.common.error.ApiErrorWriter;
import com.clinicbook.common.error.GlobalExceptionHandler;
import com.clinicbook.config.SecurityConfig;
import com.clinicbook.user.Role;
import com.clinicbook.user.dto.UserResponse;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class,
        AuthControllerTest.AdminOnlyController.class})
@ActiveProfiles("test")
class AuthControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final String LOGIN_BODY = """
            {"email":"jane@example.com","password":"password123"}
            """;
    private static final String REFRESH_BODY = """
            {"refreshToken":"refresh-token"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void allowLoginAttempts() {
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(true);
    }

    @Test
    void registerReturnsCreatedWithTokens() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"password123","fullName":"Jane Doe"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("jane@example.com"))
                .andExpect(jsonPath("$.user.role").value("PATIENT"));
    }

    @Test
    void registerRejectsInvalidInputWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("email", "password", "fullName")))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void registerReturnsConflictForAnExistingEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"password123","fullName":"Jane Doe"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
    }

    @Test
    void loginReturnsTokens() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.email").value("jane@example.com"));
    }

    @Test
    void loginReturnsUnauthorizedForBadCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginIsRateLimitedBeforeReachingTheService() throws Exception {
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(false);
        when(loginRateLimiter.windowSeconds()).thenReturn(60L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));

        verifyNoInteractions(authService);
    }

    @Test
    void refreshRotatesTokens() throws Exception {
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void refreshReturnsUnauthorizedForAnInvalidToken() throws Exception {
        when(authService.refresh(any(RefreshRequest.class)))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Refresh token is invalid"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOKEN_INVALID"));
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
    }

    @Test
    void meRejectsAnInvalidAccessToken() throws Exception {
        when(jwtService.parseAccessToken("bad-token")).thenThrow(new JwtException("invalid"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void meReturnsTheAuthenticatedUser() throws Exception {
        when(jwtService.parseAccessToken("good-token"))
                .thenReturn(new JwtPrincipal(42L, "jane@example.com", Role.PATIENT, "Jane Doe"));
        when(authService.me(42L)).thenReturn(userResponse());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void methodSecurityReturnsForbiddenForTheWrongRole() throws Exception {
        mockMvc.perform(get("/test/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void methodSecurityAllowsTheRequiredRole() throws Exception {
        mockMvc.perform(get("/test/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    private static AuthResponse authResponse() {
        return new AuthResponse("access-token", "refresh-token", "Bearer", 900, userResponse());
    }

    private static UserResponse userResponse() {
        return new UserResponse(42L, "jane@example.com", "Jane Doe", null,
                Role.PATIENT, true, CREATED_AT);
    }

    @RestController
    static class AdminOnlyController {

        @GetMapping("/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "ok";
        }
    }
}

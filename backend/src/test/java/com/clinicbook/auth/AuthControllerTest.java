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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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

    @ParameterizedTest
    @ValueSource(strings = {
            "user@gmail",             // reported bug: missing TLD
            "user@localhost",         // single-label domain
            "user@com",               // single-label domain
            "user@example.c",         // one-character TLD
            "user@example.123",       // numeric TLD
            "user@example.c0m",       // digit in TLD
            "user@example..com",      // empty domain label
            "user@-example.com",      // label starts with hyphen
            "user@example-.com",      // label ends with hyphen
            "user@.com",              // empty first label
            "user@@example.com",      // duplicate @
            "user name@example.com",  // whitespace in local part
            "plainaddress",
            "@example.com",
            "user@"
    })
    void registerRejectsEmailsWithoutARealDomain(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Jane Doe"}
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jane@example.com",
            "jane.doe+tag@sub.example.co.uk",
            "JANE@EXAMPLE.COM",
            "a@b.co",
            "user_name@example.io",
            "o'brien@example.com",
            "user@example.xn--p1ai"
    })
    void registerAcceptsUsableEmails(String email) throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Jane Doe"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-------",
            "123456",
            "1234567890123456",
            "++123456789",
            "+ 123456789",
            "call-me-now",
            "123 456",
            "1234567890123456789012345678901"
    })
    void registerRejectsInvalidPhoneNumbers(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"password123","fullName":"Jane Doe","phone":"%s"}
                                """.formatted(phone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phone")));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+1 234 567 8900",
            "1234567",
            "123-456-7890",
            "123456789012345",
            "+44 (0)20 7946 0958"
    })
    void registerAcceptsValidPhoneNumbers(String phone) throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"password123","fullName":"Jane Doe","phone":"%s"}
                                """.formatted(phone)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert('xss')</script>",
            "Jane\\nDoe",
            "Jane 😀 Doe",
            "...",
            "12345",
            "Jane_Doe",
            "Jane@Doe"
    })
    void registerRejectsUnsafeOrNonNameValues(String fullName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"password123","fullName":"%s"}
                                """.formatted(fullName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fullName")));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Jane Doe",
            "O'Brien",
            "Jean-Luc Picard",
            "María José",
            "李小龙",
            "Jane Doe Jr.",
            "A"
    })
    void registerAcceptsRealNames(String fullName) throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"password123","fullName":"%s"}
                                """.formatted(fullName)))
                .andExpect(status().isCreated());
    }

    @Test
    void registerRejectsDuplicateJsonFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","email":"other@example.com","password":"password123","fullName":"Jane Doe"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));

        verifyNoInteractions(authService);
    }

    @Test
    void loginRejectsAnEmailWithoutARealDomain() throws Exception {        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@gmail","password":"password123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")));

        verifyNoInteractions(authService);
    }

    @Test
    void registerReturnsConflictForAnExistingEmail() throws Exception {        when(authService.register(any(RegisterRequest.class)))
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
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
    }

    @Test
    void methodNotAllowedAdvertisesSupportedMethods() throws Exception {
        mockMvc.perform(get("/api/v1/auth/register"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
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

    @Test
    void registerTrimsEmailNameAndPhoneBeforeValidation() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"  Jane@Example.COM  ","password":"password123","fullName":"  Jane Doe  ","phone":"  +1 (555) 123-4567  "}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(captor.capture());
        RegisterRequest captured = captor.getValue();
        assertThat(captured.email()).isEqualTo("Jane@Example.COM");
        assertThat(captured.fullName()).isEqualTo("Jane Doe");
        assertThat(captured.phone()).isEqualTo("+1 (555) 123-4567");
    }

    @Test
    void registerTreatsWhitespaceOnlyPhoneAsEmpty() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"password123","fullName":"Jane Doe","phone":"   "}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(captor.capture());
        assertThat(captor.getValue().phone()).isEmpty();
    }

    @Test
    void registerRejectsPasswordsLongerThan72Utf8Bytes() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"%s","fullName":"Jane Doe"}
                                """.formatted("é".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));

        verifyNoInteractions(authService);
    }

    @Test
    void loginTrimsEmailBeforeValidation() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"  jane@example.com  ","password":"password123"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("jane@example.com");
    }

    @Test
    void loginRejectsPasswordsLongerThan72Utf8Bytes() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"%s"}
                                """.formatted("中".repeat(30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verifyNoInteractions(authService);
    }

    @Test
    void unsupportedAcceptReturnsAJson406InsteadOfLeakingAnErrorDispatch() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("""
                                {"email":"jane@example.com","password":"password123","fullName":"Jane Doe"}
                                """))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.error").value("NOT_ACCEPTABLE"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        verifyNoInteractions(authService);
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

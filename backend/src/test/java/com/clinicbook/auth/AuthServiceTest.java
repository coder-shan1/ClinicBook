package com.clinicbook.auth;

import com.clinicbook.auth.dto.AuthResponse;
import com.clinicbook.auth.dto.LoginRequest;
import com.clinicbook.auth.dto.LogoutRequest;
import com.clinicbook.auth.dto.RefreshRequest;
import com.clinicbook.auth.dto.RegisterRequest;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import com.clinicbook.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "clinicbook",
            "0123456789abcdef0123456789abcdef0123456789abcdef",
            Duration.ofMinutes(15),
            Duration.ofDays(7));

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private final TokenHasher tokenHasher = new TokenHasher();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
                jwtService, tokenHasher, JWT_PROPERTIES, clock);
    }

    @Test
    void registerCreatesPatientAndIssuesTokens() {
        RegisterRequest request = new RegisterRequest(
                "  Jane.Doe@Example.COM ", "password123", " Jane Doe ", "   ");
        when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            ReflectionTestUtils.setField(saved, "createdAt", NOW);
            return saved;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.user().id()).isEqualTo(7L);
        assertThat(response.user().role()).isEqualTo(Role.PATIENT);
        assertThat(response.user().email()).isEqualTo("jane.doe@example.com");
        assertThat(response.user().phone()).isNull();
        assertThat(response.user().createdAt()).isEqualTo(NOW);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.PATIENT);
        assertThat(savedUser.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(savedUser.getFullName()).isEqualTo("Jane Doe");
        assertThat(savedUser.getPhone()).isNull();

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getTokenHash()).isEqualTo(tokenHasher.hash(response.refreshToken()));
        assertThat(savedToken.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(savedToken.isRevoked()).isFalse();
        assertThat(savedToken.getUser()).isSameAs(savedUser);
    }

    @Test
    void registerRejectsAnExistingEmail() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("jane@example.com", "password123", "Jane Doe", null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("EMAIL_TAKEN");
                });

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(refreshTokenRepository, jwtService);
    }

    @Test
    void registerMapsAUniqueConstraintRaceToEmailTaken() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("jane@example.com", "password123", "Jane Doe", null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("EMAIL_TAKEN");
                });
    }

    @Test
    void loginReturnsTokensForValidCredentials() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");

        AuthResponse response = authService.login(new LoginRequest(" Jane@Example.com ", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().id()).isEqualTo(5L);
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginUsesTheSameErrorForUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
        verifyNoInteractions(jwtService);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void loginRejectsAWrongPassword() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong-password")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void loginRejectsADisabledAccount() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        user.disable();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "password123")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("ACCOUNT_DISABLED");
                });
        verifyNoInteractions(jwtService);
    }

    @Test
    void refreshRotatesTheRefreshToken() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        String rawToken = tokenHasher.newToken();
        RefreshToken stored = new RefreshToken(
                user, tokenHasher.hash(rawToken), NOW.plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(rawToken)))
                .thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");

        AuthResponse response = authService.refresh(new RefreshRequest(rawToken));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isNotEqualTo(rawToken);
        assertThat(stored.isRevoked()).isTrue();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken rotated = captor.getValue();
        assertThat(rotated.getTokenHash()).isEqualTo(tokenHasher.hash(response.refreshToken()));
        assertThat(rotated.getTokenHash()).isNotEqualTo(stored.getTokenHash());
        assertThat(rotated.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(rotated.getUser()).isSameAs(user);
    }

    @Test
    void refreshRejectsAnUnknownToken() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("unknown")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("TOKEN_INVALID");
                });
        verifyNoInteractions(jwtService);
    }

    @Test
    void refreshRejectsARevokedToken() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        String rawToken = tokenHasher.newToken();
        RefreshToken stored = new RefreshToken(
                user, tokenHasher.hash(rawToken), NOW.plus(Duration.ofDays(1)));
        stored.revoke();
        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(rawToken)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("TOKEN_REVOKED");
                });
        verifyNoInteractions(jwtService);
    }

    @Test
    void refreshRejectsAnExpiredToken() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        String rawToken = tokenHasher.newToken();
        RefreshToken stored = new RefreshToken(user, tokenHasher.hash(rawToken), NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(rawToken)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("TOKEN_EXPIRED");
                });
        verifyNoInteractions(jwtService);
    }

    @Test
    void refreshRejectsADisabledAccount() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        user.disable();
        String rawToken = tokenHasher.newToken();
        RefreshToken stored = new RefreshToken(user, tokenHasher.hash(rawToken), NOW.plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(rawToken)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("ACCOUNT_DISABLED");
                });
        verifyNoInteractions(jwtService);
    }

    @Test
    void logoutRevokesTheStoredRefreshToken() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        String rawToken = tokenHasher.newToken();
        RefreshToken stored = new RefreshToken(user, tokenHasher.hash(rawToken), NOW.plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(rawToken)))
                .thenReturn(Optional.of(stored));

        authService.logout(new LogoutRequest(rawToken));

        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void logoutIsIdempotentForUnknownTokens() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> authService.logout(new LogoutRequest("unknown")))
                .doesNotThrowAnyException();
    }

    @Test
    void meReturnsTheCurrentUser() {
        User user = user(5L, "jane@example.com", "bcrypt-hash");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        UserResponse response = authService.me(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void meRejectsAMissingUser() {
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(5L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("UNAUTHORIZED");
                });
    }

    private static User user(Long id, String email, String passwordHash) {
        User user = new User(email, passwordHash, Role.PATIENT, "Jane Doe", null);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", NOW);
        return user;
    }
}

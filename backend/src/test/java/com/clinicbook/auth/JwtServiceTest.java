package com.clinicbook.auth;

import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET = "fedcba9876543210fedcba9876543210fedcba9876543210";

    private final JwtProperties properties = new JwtProperties(
            "clinicbook", SECRET, Duration.ofMinutes(15), Duration.ofDays(7));
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(properties, clock);

    @Test
    void generatesAndParsesAccessTokenClaims() {
        String token = jwtService.generateAccessToken(user(42L, Role.DOCTOR));

        JwtPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.id()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("jane.doe@example.com");
        assertThat(principal.role()).isEqualTo(Role.DOCTOR);
        assertThat(principal.fullName()).isEqualTo("Jane Doe");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateAccessToken(user(42L, Role.PATIENT));
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService foreignService = new JwtService(
                new JwtProperties("clinicbook", OTHER_SECRET, Duration.ofMinutes(15), Duration.ofDays(7)),
                clock);
        String foreignToken = foreignService.generateAccessToken(user(42L, Role.PATIENT));

        assertThatThrownBy(() -> jwtService.parseAccessToken(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = jwtService.generateAccessToken(user(42L, Role.PATIENT));
        JwtService laterService = new JwtService(properties, Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> laterService.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtService foreignIssuer = new JwtService(
                new JwtProperties("somebody-else", SECRET, Duration.ofMinutes(15), Duration.ofDays(7)),
                clock);
        String token = foreignIssuer.generateAccessToken(user(42L, Role.PATIENT));

        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesASecretShorterThanThirtyTwoBytes() {
        JwtProperties weak = new JwtProperties(
                "clinicbook", "too-short", Duration.ofMinutes(15), Duration.ofDays(7));

        assertThatThrownBy(() -> new JwtService(weak, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }

    private static User user(Long id, Role role) {
        User user = new User("jane.doe@example.com", "hash", role, "Jane Doe", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

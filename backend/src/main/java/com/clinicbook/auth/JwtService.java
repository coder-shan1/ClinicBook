package com.clinicbook.auth;

import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;

        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HMAC signing");
        }
        requirePositive(properties.accessTokenTtl(), "app.jwt.access-token-ttl");
        requirePositive(properties.refreshTokenTtl(), "app.jwt.refresh-token-ttl");
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, then rebuilds the caller identity.
     *
     * @throws JwtException when the token is malformed, tampered with, expired or missing claims
     */
    public JwtPrincipal parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Object rawUserId = claims.get("uid");
        if (!(rawUserId instanceof Number userId)) {
            throw new JwtException("Access token is missing the user id claim");
        }

        String roleClaim = claims.get("role", String.class);
        Role role;
        try {
            role = Role.valueOf(roleClaim);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new JwtException("Access token has an invalid role claim");
        }

        String email = claims.getSubject();
        if (email == null || email.isBlank()) {
            throw new JwtException("Access token is missing the subject claim");
        }

        return new JwtPrincipal(userId.longValue(), email, role, claims.get("name", String.class));
    }

    private static void requirePositive(Duration duration, String property) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(property + " must be positive");
        }
    }
}

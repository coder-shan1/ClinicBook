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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHasher tokenHasher;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenHasher tokenHasher,
                       JwtProperties jwtProperties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHasher = tokenHasher;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        // Verified against when the email is unknown, so login timing does not reveal valid accounts.
        this.dummyPasswordHash = passwordEncoder.encode("clinicbook-timing-equaliser");
    }

    /**
     * Public registration always creates a PATIENT. Roles are assigned by an administrator,
     * never by the caller.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                Role.PATIENT,
                request.fullName().trim(),
                normalisePhone(request.phone()));

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw emailTaken();
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normaliseEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account is disabled");
        }
        return issueTokens(user);
    }

    /**
     * Rotates the refresh token: the presented token is revoked and a new pair is issued.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHasher.hash(request.refreshToken()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Refresh token is invalid"));

        if (stored.isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED", "Refresh token has been revoked");
        }
        if (!stored.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Refresh token has expired");
        }

        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account is disabled");
        }

        stored.revoke();
        return issueTokens(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(request.refreshToken()))
                .ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Account no longer exists"));
    }

    private AuthResponse issueTokens(User user) {
        Instant now = clock.instant();
        String rawRefreshToken = tokenHasher.newToken();
        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHasher.hash(rawRefreshToken),
                now.plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.generateAccessToken(user);
        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtProperties.accessTokenTtl().toSeconds(),
                UserResponse.from(user));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalisePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS_MESSAGE);
    }
}

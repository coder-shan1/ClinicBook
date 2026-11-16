package com.clinicbook.auth;

import com.clinicbook.auth.dto.AuthResponse;
import com.clinicbook.auth.dto.LoginRequest;
import com.clinicbook.auth.dto.LogoutRequest;
import com.clinicbook.auth.dto.RefreshRequest;
import com.clinicbook.auth.dto.RegisterRequest;
import com.clinicbook.common.error.ApiError;
import com.clinicbook.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Registration, login and token lifecycle")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a patient account and start a session")
    @ApiResponse(responseCode = "201", description = "Account created; access and refresh tokens returned",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Malformed body or failed field validation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email is already registered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for tokens")
    @ApiResponse(responseCode = "200", description = "Authenticated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Malformed body or failed field validation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials or disabled account",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Too many login attempts",
            headers = @Header(name = "Retry-After", description = "Seconds until the next attempt is allowed",
                    schema = @Schema(type = "integer")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new token pair")
    @ApiResponse(responseCode = "200", description = "Tokens rotated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Malformed body or failed field validation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Refresh token is invalid, expired or revoked",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token")
    @ApiResponse(responseCode = "204", description = "Token revoked or already invalid")
    @ApiResponse(responseCode = "400", description = "Malformed body or failed field validation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Current user",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public UserResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        return authService.me(principal.id());
    }
}

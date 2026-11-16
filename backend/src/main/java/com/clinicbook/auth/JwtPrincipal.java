package com.clinicbook.auth;

import com.clinicbook.user.Role;

/**
 * Authenticated caller reconstructed from access-token claims. Kept free of JPA so
 * controllers can receive it via {@code @AuthenticationPrincipal}.
 */
public record JwtPrincipal(Long id, String email, Role role, String fullName) {
}

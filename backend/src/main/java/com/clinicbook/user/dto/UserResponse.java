package com.clinicbook.user.dto;

import com.clinicbook.user.Role;
import com.clinicbook.user.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}

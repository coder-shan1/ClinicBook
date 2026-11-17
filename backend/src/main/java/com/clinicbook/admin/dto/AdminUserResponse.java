package com.clinicbook.admin.dto;

import com.clinicbook.user.Role;
import com.clinicbook.user.User;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        boolean enabled,
        Instant createdAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}

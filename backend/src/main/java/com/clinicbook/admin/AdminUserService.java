package com.clinicbook.admin;

import com.clinicbook.admin.dto.AdminUserResponse;
import com.clinicbook.admin.dto.UpdateUserRoleRequest;
import com.clinicbook.admin.dto.UpdateUserStatusRequest;
import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.auth.RefreshTokenRepository;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Administrator user administration with safeguards: nobody can change their own
 * account here, the last enabled administrator cannot be removed, and doctor accounts
 * are managed through the doctor endpoints so their profile cannot drift.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DoctorRepository doctorRepository;

    public AdminUserService(UserRepository userRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            DoctorRepository doctorRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(Role role, String q, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt")));
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        Page<User> users = role == null
                ? userRepository.search(query, pageable)
                : userRepository.searchByRole(role, query, pageable);
        return PageResponse.from(users, AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse updateStatus(Long targetId, UpdateUserStatusRequest request,
                                          JwtPrincipal principal) {
        requireNotSelf(targetId, principal);
        User target = userRepository.findByIdForUpdate(targetId)
                .orElseThrow(AdminUserService::userNotFound);

        if (request.enabled()) {
            target.enable();
            return AdminUserResponse.from(target);
        }

        ensureNotLastAdmin(target);
        target.disable();
        if (target.getRole() == Role.DOCTOR) {
            // A disabled doctor must also disappear from public search.
            doctorRepository.findByUserId(targetId).ifPresent(doctor -> doctor.deactivate());
        }
        refreshTokenRepository.revokeAllByUserId(targetId);
        return AdminUserResponse.from(target);
    }

    @Transactional
    public AdminUserResponse updateRole(Long targetId, UpdateUserRoleRequest request,
                                        JwtPrincipal principal) {
        requireNotSelf(targetId, principal);
        User target = userRepository.findByIdForUpdate(targetId)
                .orElseThrow(AdminUserService::userNotFound);

        Role newRole = request.role();
        if (target.getRole() == newRole) {
            return AdminUserResponse.from(target);
        }
        if (target.getRole() == Role.DOCTOR || newRole == Role.DOCTOR) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCTOR_ROLE_MANAGED_SEPARATELY",
                    "Doctor accounts are managed through the doctor endpoints");
        }

        ensureNotLastAdmin(target);
        target.changeRole(newRole);
        // Force a fresh login so the new role is reflected in issued access tokens.
        refreshTokenRepository.revokeAllByUserId(targetId);
        return AdminUserResponse.from(target);
    }

    private void ensureNotLastAdmin(User target) {
        if (!target.isEnabled() || target.getRole() != Role.ADMIN) {
            return;
        }
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN",
                    "The last enabled administrator cannot be disabled or demoted");
        }
    }

    private static void requireNotSelf(Long targetId, JwtPrincipal principal) {
        if (principal.id().equals(targetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "SELF_MODIFICATION_NOT_ALLOWED",
                    "Administrators cannot change their own role or status");
        }
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
    }
}

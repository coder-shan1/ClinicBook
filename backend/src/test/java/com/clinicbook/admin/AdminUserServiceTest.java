package com.clinicbook.admin;

import com.clinicbook.admin.dto.AdminUserResponse;
import com.clinicbook.admin.dto.UpdateUserRoleRequest;
import com.clinicbook.admin.dto.UpdateUserStatusRequest;
import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.auth.RefreshTokenRepository;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final JwtPrincipal ADMIN = new JwtPrincipal(99L, "admin@example.com",
            Role.ADMIN, "Admin");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private DoctorRepository doctorRepository;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, refreshTokenRepository, doctorRepository);
    }

    @Test
    void rejectsChangingYourOwnStatus() {
        assertApiError(() -> service.updateStatus(99L, new UpdateUserStatusRequest(false), ADMIN),
                HttpStatus.CONFLICT, "SELF_MODIFICATION_NOT_ALLOWED");
        verify(userRepository, never()).findByIdForUpdate(99L);
    }

    @Test
    void rejectsChangingYourOwnRole() {
        assertApiError(() -> service.updateRole(99L, new UpdateUserRoleRequest(Role.PATIENT), ADMIN),
                HttpStatus.CONFLICT, "SELF_MODIFICATION_NOT_ALLOWED");
        verify(userRepository, never()).findByIdForUpdate(99L);
    }

    @Test
    void cannotDisableTheLastEnabledAdministrator() {
        User target = user(1L, Role.ADMIN, true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertApiError(() -> service.updateStatus(1L, new UpdateUserStatusRequest(false), ADMIN),
                HttpStatus.CONFLICT, "LAST_ADMIN");
        assertThat(target.isEnabled()).isTrue();
        verify(refreshTokenRepository, never()).revokeAllByUserId(1L);
    }

    @Test
    void disablesAnotherAdministratorWhenOneRemains() {
        User target = user(1L, Role.ADMIN, true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);

        AdminUserResponse response = service.updateStatus(
                1L, new UpdateUserStatusRequest(false), ADMIN);

        assertThat(response.enabled()).isFalse();
        assertThat(target.isEnabled()).isFalse();
        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    void disablingADoctorAlsoDeactivatesTheDoctorProfile() {
        User target = user(5L, Role.DOCTOR, true);
        Doctor doctor = doctor(7L, target);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(target));
        when(doctorRepository.findByUserId(5L)).thenReturn(Optional.of(doctor));

        AdminUserResponse response = service.updateStatus(
                5L, new UpdateUserStatusRequest(false), ADMIN);

        assertThat(response.enabled()).isFalse();
        assertThat(doctor.isActive()).isFalse();
        verify(refreshTokenRepository).revokeAllByUserId(5L);
    }

    @Test
    void enablingAnAccountDoesNotRevokeItsRefreshTokens() {
        User target = user(5L, Role.PATIENT, false);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(target));

        AdminUserResponse response = service.updateStatus(
                5L, new UpdateUserStatusRequest(true), ADMIN);

        assertThat(response.enabled()).isTrue();
        verify(refreshTokenRepository, never()).revokeAllByUserId(5L);
    }

    @Test
    void reportsAnUnknownUser() {
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        assertApiError(() -> service.updateStatus(5L, new UpdateUserStatusRequest(false), ADMIN),
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void blocksChangingADoctorToAnotherRole() {
        User target = user(5L, Role.DOCTOR, true);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(target));

        assertApiError(() -> service.updateRole(5L, new UpdateUserRoleRequest(Role.PATIENT), ADMIN),
                HttpStatus.CONFLICT, "DOCTOR_ROLE_MANAGED_SEPARATELY");
        assertThat(target.getRole()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void blocksPromotingAUserToDoctor() {
        User target = user(5L, Role.PATIENT, true);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(target));

        assertApiError(() -> service.updateRole(5L, new UpdateUserRoleRequest(Role.DOCTOR), ADMIN),
                HttpStatus.CONFLICT, "DOCTOR_ROLE_MANAGED_SEPARATELY");
    }

    @Test
    void cannotDemoteTheLastEnabledAdministrator() {
        User target = user(1L, Role.ADMIN, true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertApiError(() -> service.updateRole(1L, new UpdateUserRoleRequest(Role.PATIENT), ADMIN),
                HttpStatus.CONFLICT, "LAST_ADMIN");
        assertThat(target.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void promotesAPatientAndRevokesTheirRefreshTokens() {
        User target = user(5L, Role.PATIENT, true);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(target));

        AdminUserResponse response = service.updateRole(
                5L, new UpdateUserRoleRequest(Role.ADMIN), ADMIN);

        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(target.getRole()).isEqualTo(Role.ADMIN);
        verify(refreshTokenRepository).revokeAllByUserId(5L);
    }

    @Test
    void changingToTheSameRoleIsANoOp() {
        User target = user(5L, Role.PATIENT, true);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(target));

        AdminUserResponse response = service.updateRole(
                5L, new UpdateUserRoleRequest(Role.PATIENT), ADMIN);

        assertThat(response.role()).isEqualTo(Role.PATIENT);
        verify(refreshTokenRepository, never()).revokeAllByUserId(5L);
    }

    private static void assertApiError(ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        ApiException ex = catchThrowableOfType(call, ApiException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(status);
        assertThat(ex.getCode()).isEqualTo(code);
    }

    private static User user(Long id, Role role, boolean enabled) {
        User user = new User("user" + id + "@example.com", "hash", role, "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        if (!enabled) {
            user.disable();
        }
        return user;
    }

    private static Doctor doctor(Long id, User user) {
        Specialty specialty = new Specialty("Cardiology", null);
        ReflectionTestUtils.setField(specialty, "id", 2L);
        Doctor doctor = new Doctor(user, specialty, null, new BigDecimal("500.00"));
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }
}

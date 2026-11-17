package com.clinicbook.admin;

import com.clinicbook.admin.dto.AdminStatsResponse;
import com.clinicbook.admin.dto.AdminUserResponse;
import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.auth.JwtService;
import com.clinicbook.auth.LoginRateLimiter;
import com.clinicbook.auth.RestAccessDeniedHandler;
import com.clinicbook.auth.RestAuthenticationEntryPoint;
import com.clinicbook.common.error.ApiErrorWriter;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.common.error.GlobalExceptionHandler;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.config.SecurityConfig;
import com.clinicbook.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminStatsController.class, AdminUserController.class})
@Import({SecurityConfig.class, ApiErrorWriter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminControllerTest {

    private static final AdminUserResponse USER = new AdminUserResponse(
            5L, "user@example.com", "User Five", null, Role.PATIENT, true,
            Instant.parse("2026-01-01T00:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminStatsService adminStatsService;
    @MockitoBean
    private AdminUserService adminUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private LoginRateLimiter loginRateLimiter;

    @Test
    void adminReadsStatistics() throws Exception {
        AdminStatsResponse stats = new AdminStatsResponse(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
                4, 1, 1, 1, 1, 0, new BigDecimal("0.2500"),
                List.of(new AdminStatsResponse.DailyBookings(LocalDate.of(2026, 1, 1), 4)),
                List.of(new AdminStatsResponse.SpecialtyBookings(2L, "Cardiology", 4)),
                List.of(new AdminStatsResponse.TopDoctor(7L, "Dr. Test", "Cardiology",
                        4, 1, new BigDecimal("4.50"), 2)));
        when(adminStatsService.stats(null, null, 5)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/admin/stats").with(asRole(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(4))
                .andExpect(jsonPath("$.cancellationRate").value(0.2500))
                .andExpect(jsonPath("$.bookingsPerDay[0].bookings").value(4))
                .andExpect(jsonPath("$.topDoctors[0].doctorName").value("Dr. Test"));
    }

    @Test
    void patientCannotReadStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats").with(asRole(Role.PATIENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void anonymousCallerCannotReadStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statisticsRejectAnOversizedTopDoctorLimit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats")
                        .with(asRole(Role.ADMIN))
                        .param("topDoctors", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void adminListsUsers() throws Exception {
        when(adminUserService.list(eq(null), eq(null), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(USER), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/admin/users").with(asRole(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.content[0].enabled").value(true));
    }

    @Test
    void patientCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(asRole(Role.PATIENT)))
                .andExpect(status().isForbidden());

        verify(adminUserService, never()).list(any(), any(), anyInt(), anyInt());
    }

    @Test
    void adminUpdatesAUserStatus() throws Exception {
        when(adminUserService.updateStatus(eq(5L), any(), any(JwtPrincipal.class)))
                .thenReturn(new AdminUserResponse(5L, "user@example.com", "User Five", null,
                        Role.PATIENT, false, USER.createdAt()));

        mockMvc.perform(patch("/api/v1/admin/users/5/status")
                        .with(asRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void statusUpdateRequiresTheEnabledFlag() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/5/status")
                        .with(asRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("enabled"));
    }

    @Test
    void roleUpdateRequiresAValidRole() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/5/role")
                        .with(asRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void selfModificationConflictIsReported() throws Exception {
        when(adminUserService.updateRole(eq(5L), any(), any(JwtPrincipal.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "SELF_MODIFICATION_NOT_ALLOWED",
                        "Administrators cannot change their own role or status"));

        mockMvc.perform(patch("/api/v1/admin/users/5/role")
                        .with(asRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PATIENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SELF_MODIFICATION_NOT_ALLOWED"));
    }

    private static RequestPostProcessor asRole(Role role) {
        JwtPrincipal principal = new JwtPrincipal(role == Role.DOCTOR ? 7L : 99L,
                "admin@example.com", role, "User");
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}

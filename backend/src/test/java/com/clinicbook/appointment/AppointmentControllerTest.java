package com.clinicbook.appointment;

import com.clinicbook.appointment.dto.AppointmentResponse;
import com.clinicbook.appointment.dto.CreateAppointmentRequest;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AppointmentController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AppointmentControllerTest {

    private static final Instant START = Instant.parse("2026-01-10T09:00:00Z");
    private static final Instant END = Instant.parse("2026-01-10T09:30:00Z");
    private static final AppointmentResponse PENDING = new AppointmentResponse(
            50L, 1L, "Patient One", 7L, "Dr. Test", 10L, START, END,
            AppointmentStatus.PENDING, Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"));
    private static final AppointmentResponse CANCELLED = new AppointmentResponse(
            50L, 1L, "Patient One", 7L, "Dr. Test", null, START, END,
            AppointmentStatus.CANCELLED, Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private LoginRateLimiter loginRateLimiter;

    @Test
    void patientBooksASlot() throws Exception {
        when(appointmentService.book(eq(1L), any(CreateAppointmentRequest.class))).thenReturn(PENDING);

        mockMvc.perform(post("/api/v1/appointments")
                        .with(asRole(Role.PATIENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":10}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/appointments/50"))
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.slotId").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.doctorName").value("Dr. Test"));
    }

    @Test
    void doctorCannotBookASlot() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(asRole(Role.DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(appointmentService, never()).book(any(), any());
    }

    @Test
    void anonymousCallerCannotBook() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookingRequiresASlotId() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(asRole(Role.PATIENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slotId"));
    }

    @Test
    void bookingRejectsANonNumericSlotId() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(asRole(Role.PATIENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"ten\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void bookingReportsATakenSlotAsConflict() throws Exception {
        when(appointmentService.book(eq(1L), any(CreateAppointmentRequest.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "SLOT_TAKEN", "This slot has already been booked"));

        mockMvc.perform(post("/api/v1/appointments")
                        .with(asRole(Role.PATIENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_TAKEN"));
    }

    @Test
    void patientListsTheirAppointments() throws Exception {
        when(appointmentService.listMine(any(JwtPrincipal.class), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(PENDING), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/appointments/me").with(asRole(Role.PATIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void doctorListsTheirAppointments() throws Exception {
        when(appointmentService.listMine(any(JwtPrincipal.class), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(PENDING), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/appointments/me").with(asRole(Role.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].doctorId").value(7));
    }

    @Test
    void adminHasNoPersonalAppointmentList() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/me").with(asRole(Role.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void listRejectsAnOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/me")
                        .with(asRole(Role.PATIENT))
                        .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void patientCancelsAnAppointment() throws Exception {
        when(appointmentService.cancel(eq(50L), any(JwtPrincipal.class))).thenReturn(CANCELLED);

        mockMvc.perform(patch("/api/v1/appointments/50/cancel").with(asRole(Role.PATIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.slotId").doesNotExist());
    }

    @Test
    void cancellationInsideTheCutoffReturnsConflict() throws Exception {
        when(appointmentService.cancel(eq(50L), any(JwtPrincipal.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "CANCELLATION_WINDOW_CLOSED",
                        "Appointments can only be cancelled before the cancellation cutoff"));

        mockMvc.perform(patch("/api/v1/appointments/50/cancel").with(asRole(Role.PATIENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CANCELLATION_WINDOW_CLOSED"));
    }

    @Test
    void patientCannotConfirmAnAppointment() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/50/confirm").with(asRole(Role.PATIENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(appointmentService, never()).confirm(any(), any());
    }

    @Test
    void doctorConfirmsAnAppointment() throws Exception {
        AppointmentResponse confirmed = new AppointmentResponse(
                50L, 1L, "Patient One", 7L, "Dr. Test", 10L, START, END,
                AppointmentStatus.CONFIRMED, START, START);
        when(appointmentService.confirm(eq(50L), any(JwtPrincipal.class))).thenReturn(confirmed);

        mockMvc.perform(patch("/api/v1/appointments/50/confirm").with(asRole(Role.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void doctorCompletesAnAppointment() throws Exception {
        AppointmentResponse completed = new AppointmentResponse(
                50L, 1L, "Patient One", 7L, "Dr. Test", 10L, START, END,
                AppointmentStatus.COMPLETED, START, START);
        when(appointmentService.complete(eq(50L), any(JwtPrincipal.class))).thenReturn(completed);

        mockMvc.perform(patch("/api/v1/appointments/50/complete").with(asRole(Role.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void doctorMarksANoShow() throws Exception {
        AppointmentResponse noShow = new AppointmentResponse(
                50L, 1L, "Patient One", 7L, "Dr. Test", 10L, START, END,
                AppointmentStatus.NO_SHOW, START, START);
        when(appointmentService.noShow(eq(50L), any(JwtPrincipal.class))).thenReturn(noShow);

        mockMvc.perform(patch("/api/v1/appointments/50/no-show").with(asRole(Role.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void invalidTransitionReturnsConflict() throws Exception {
        when(appointmentService.complete(eq(50L), any(JwtPrincipal.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "INVALID_APPOINTMENT_STATE",
                        "Cannot change appointment from PENDING to COMPLETED"));

        mockMvc.perform(patch("/api/v1/appointments/50/complete").with(asRole(Role.DOCTOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_APPOINTMENT_STATE"));
    }

    @Test
    void unknownAppointmentReturnsNotFound() throws Exception {
        when(appointmentService.cancel(eq(99L), any(JwtPrincipal.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "Appointment not found"));

        mockMvc.perform(patch("/api/v1/appointments/99/cancel").with(asRole(Role.PATIENT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("APPOINTMENT_NOT_FOUND"));
    }

    private static RequestPostProcessor asRole(Role role) {
        JwtPrincipal principal = new JwtPrincipal(role == Role.DOCTOR ? 7L : 1L,
                "user@example.com", role, "User");
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}

package com.clinicbook.doctor;

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
import com.clinicbook.doctor.dto.DoctorResponse;
import com.clinicbook.doctor.dto.DoctorSummaryResponse;
import com.clinicbook.doctor.dto.SpecialtyResponse;
import com.clinicbook.schedule.ScheduleController;
import com.clinicbook.schedule.ScheduleService;
import com.clinicbook.schedule.SlotGenerationResult;
import com.clinicbook.schedule.TimeSlotStatus;
import com.clinicbook.schedule.dto.SlotGenerationResponse;
import com.clinicbook.schedule.dto.TimeOffResponse;
import com.clinicbook.schedule.dto.TimeSlotResponse;
import com.clinicbook.schedule.dto.WorkingHoursResponse;
import com.clinicbook.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DoctorController.class, AdminDoctorController.class,
        SpecialtyController.class, AdminSpecialtyController.class, ScheduleController.class})
@Import({SecurityConfig.class, ApiErrorWriter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ClinicControllerTest {

    private static final Instant START = Instant.parse("2026-01-05T09:00:00Z");
    private static final Instant END = Instant.parse("2026-01-05T09:30:00Z");
    private static final SpecialtyResponse CARDIOLOGY = new SpecialtyResponse(2L, "Cardiology", "Heart care");
    private static final DoctorResponse DOCTOR_RESPONSE = new DoctorResponse(
            1L, 7L, "Dr. Test", "doctor@example.com", "+91 90000 00001", CARDIOLOGY,
            "Bio", new BigDecimal("800.00"), true, Instant.parse("2026-01-01T00:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorService doctorService;
    @MockitoBean
    private SpecialtyService specialtyService;
    @MockitoBean
    private ScheduleService scheduleService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private LoginRateLimiter loginRateLimiter;

    @Test
    void publicSearchReturnsAPage() throws Exception {
        DoctorSummaryResponse summary = new DoctorSummaryResponse(
                1L, "Dr. Test", CARDIOLOGY, "Bio", new BigDecimal("800.00"));
        when(doctorService.search(eq(null), eq("heart"), eq(0), eq(20), eq("fullName,asc")))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/doctors").param("q", "heart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Dr. Test"))
                .andExpect(jsonPath("$.content[0].specialty.name").value("Cardiology"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void publicSearchRejectsAnOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/doctors").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void publicSearchRejectsANonNumericSpecialtyFilter() throws Exception {
        mockMvc.perform(get("/api/v1/doctors").param("specialty", "cardiology"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void publicDetailReturns404ForAnUnknownDoctor() throws Exception {
        when(doctorService.getPublic(99L)).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND", "Doctor not found"));

        mockMvc.perform(get("/api/v1/doctors/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCTOR_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/doctors/99"));
    }

    @Test
    void publicSlotsReturnAvailableSlots() throws Exception {
        when(doctorService.slots(eq(1L), any())).thenReturn(List.of(
                new TimeSlotResponse(10L, START, END, TimeSlotStatus.AVAILABLE)));

        mockMvc.perform(get("/api/v1/doctors/1/slots").param("date", "2026-01-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startAt").value("2026-01-05T09:00:00Z"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void doctorProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void doctorProfileRejectsTheWrongRole() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void doctorProfileReturnsTheAuthenticatedDoctor() throws Exception {
        when(doctorService.me(7L)).thenReturn(DOCTOR_RESPONSE);

        mockMvc.perform(get("/api/v1/doctors/me").with(asRole(Role.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Dr. Test"))
                .andExpect(jsonPath("$.email").value("doctor@example.com"));
    }

    @Test
    void adminCanCreateADoctor() throws Exception {
        when(doctorService.create(any())).thenReturn(DOCTOR_RESPONSE);

        mockMvc.perform(post("/api/v1/admin/doctors")
                        .with(asRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"doctor@example.com","password":"password123",
                                 "fullName":"Dr. Test","specialtyId":2,
                                 "consultationFee":800.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void adminCreateRejectsAPatient() throws Exception {
        mockMvc.perform(post("/api/v1/admin/doctors")
                        .with(asRole(Role.PATIENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"doctor@example.com","password":"password123",
                                 "fullName":"Dr. Test","specialtyId":2,
                                 "consultationFee":800.00}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDeleteDeactivatesADoctor() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/doctors/1").with(asRole(Role.ADMIN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCreateValidatesTheBody() throws Exception {
        mockMvc.perform(post("/api/v1/admin/doctors")
                        .with(asRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field").isArray());
    }

    @Test
    void specialtyCatalogueIsPublic() throws Exception {
        when(specialtyService.list()).thenReturn(List.of(CARDIOLOGY));

        mockMvc.perform(get("/api/v1/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cardiology"));
    }

    @Test
    void adminSpecialtyCreateReturnsConflictForADuplicate() throws Exception {
        when(specialtyService.create(any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "SPECIALTY_TAKEN", "Already exists"));

        mockMvc.perform(post("/api/v1/admin/specialties")
                        .with(asRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cardiology","description":"Heart care"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SPECIALTY_TAKEN"));
    }

    @Test
    void scheduleEndpointsRequireADoctor() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/me/working-hours").with(asRole(Role.PATIENT)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/doctors/me/time-off"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doctorCanReadWorkingHours() throws Exception {
        when(scheduleService.getWorkingHours(7L)).thenReturn(List.of(
                new WorkingHoursResponse(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0),
                        LocalTime.of(17, 0), 30)));

        mockMvc.perform(get("/api/v1/doctors/me/working-hours").with(asRole(Role.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].slotMinutes").value(30));
    }

    @Test
    void doctorCanReplaceWorkingHours() throws Exception {
        when(scheduleService.replaceWorkingHours(eq(7L), any())).thenReturn(List.of(
                new WorkingHoursResponse(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0),
                        LocalTime.of(17, 0), 30)));

        mockMvc.perform(put("/api/v1/doctors/me/working-hours")
                        .with(asRole(Role.DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workingHours":[{"dayOfWeek":"MONDAY","startTime":"09:00:00",
                                 "endTime":"17:00:00","slotMinutes":30}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").value("09:00:00"));
    }

    @Test
    void replaceWorkingHoursValidatesNestedEntries() throws Exception {
        mockMvc.perform(put("/api/v1/doctors/me/working-hours")
                        .with(asRole(Role.DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workingHours":[{"dayOfWeek":"MONDAY","startTime":"09:00:00",
                                 "endTime":"09:10:00","slotMinutes":0}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void doctorCanAddAndDeleteTimeOff() throws Exception {
        when(scheduleService.addTimeOff(eq(7L), any())).thenReturn(
                new TimeOffResponse(5L, START, END, "Meeting", START));

        mockMvc.perform(post("/api/v1/doctors/me/time-off")
                        .with(asRole(Role.DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startAt":"2026-01-05T09:00:00Z","endAt":"2026-01-05T09:30:00Z",
                                 "reason":"Meeting"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("Meeting"));

        mockMvc.perform(delete("/api/v1/doctors/me/time-off/5").with(asRole(Role.DOCTOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    void doctorCanRegenerateSlots() throws Exception {
        when(scheduleService.regenerateSlots(7L)).thenReturn(
                new SlotGenerationResult(START, 12, 2, 1, 10));

        mockMvc.perform(post("/api/v1/doctors/me/slots/regenerate").with(asRole(Role.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(12))
                .andExpect(jsonPath("$.available").value(10));
    }

    private static RequestPostProcessor asRole(Role role) {
        JwtPrincipal principal = new JwtPrincipal(7L, "user@example.com", role, "User");
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}

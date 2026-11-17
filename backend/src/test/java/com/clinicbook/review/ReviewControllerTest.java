package com.clinicbook.review;

import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.auth.JwtService;
import com.clinicbook.auth.LoginRateLimiter;
import com.clinicbook.auth.RestAccessDeniedHandler;
import com.clinicbook.auth.RestAuthenticationEntryPoint;
import com.clinicbook.common.error.ApiErrorWriter;
import com.clinicbook.common.error.GlobalExceptionHandler;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.config.SecurityConfig;
import com.clinicbook.review.dto.CreateReviewRequest;
import com.clinicbook.review.dto.ReviewResponse;
import com.clinicbook.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ReviewControllerTest {

    private static final ReviewResponse REVIEW = new ReviewResponse(
            30L, 50L, 7L, "Patient O.", 5, "Excellent",
            Instant.parse("2026-01-06T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private LoginRateLimiter loginRateLimiter;

    @Test
    void patientCreatesAReview() throws Exception {
        when(reviewService.create(eq(1L), any(CreateReviewRequest.class))).thenReturn(REVIEW);

        mockMvc.perform(post("/api/v1/reviews")
                        .with(asRole(Role.PATIENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":50,\"rating\":5,\"comment\":\"Excellent\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/doctors/7/reviews"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.patientName").value("Patient O."));
    }

    @Test
    void doctorCannotCreateAReview() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .with(asRole(Role.DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":50,\"rating\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(reviewService, never()).create(any(), any());
    }

    @Test
    void anonymousCallerCannotCreateAReview() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":50,\"rating\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reviewRequiresARatingInsideTheRange() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .with(asRole(Role.PATIENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":50,\"rating\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
    }

    @Test
    void publicDoctorReviewsArePaginated() throws Exception {
        when(reviewService.listForDoctor(eq(7L), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(REVIEW), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/doctors/7/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listRejectsAnOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/7/reviews").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    private static RequestPostProcessor asRole(Role role) {
        JwtPrincipal principal = new JwtPrincipal(role == Role.DOCTOR ? 7L : 1L,
                "user@example.com", role, "User");
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}

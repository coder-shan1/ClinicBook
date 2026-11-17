package com.clinicbook.extras;

import com.clinicbook.appointment.Appointment;
import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.doctor.SpecialtyRepository;
import com.clinicbook.notification.EmailService;
import com.clinicbook.notification.NotificationLogRepository;
import com.clinicbook.notification.NotificationService;
import com.clinicbook.notification.NotificationType;
import com.clinicbook.review.ReviewService;
import com.clinicbook.review.dto.CreateReviewRequest;
import com.clinicbook.review.dto.ReviewResponse;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Proves the reminder and review rules against a real database. Subclasses supply MySQL
 * or PostgreSQL; the suite skips itself when Docker is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.notifications.enabled=true")
@ActiveProfiles("test")
abstract class AbstractExtrasDatabaseTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationLogRepository notificationLogRepository;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecialtyRepository specialtyRepository;

    @MockitoBean
    private EmailService emailService;

    @Test
    void reminderIsSentExactlyOncePerAppointment() {
        Appointment appointment = seedAppointment(AppointmentStatus.CONFIRMED,
                Instant.now().plus(12, ChronoUnit.HOURS));

        assertThat(notificationService.sendReminder(appointment.getId())).isTrue();
        assertThat(notificationService.sendReminder(appointment.getId())).isFalse();

        assertThat(notificationLogRepository.countByAppointmentId(appointment.getId())).isEqualTo(1);
        assertThat(notificationLogRepository.existsByAppointmentIdAndType(
                appointment.getId(), NotificationType.REMINDER)).isTrue();
        verify(emailService, times(1)).send(any(), eq(NotificationType.REMINDER));
    }

    @Test
    void reminderSkipsAppointmentsOutsideTheLeadWindow() {
        Appointment appointment = seedAppointment(AppointmentStatus.CONFIRMED,
                Instant.now().plus(30, ChronoUnit.HOURS));

        assertThat(notificationService.sendReminder(appointment.getId())).isFalse();

        assertThat(notificationLogRepository.countByAppointmentId(appointment.getId())).isZero();
    }

    @Test
    void reviewRequiresACompletedAppointmentAndIsAllowedOnlyOnce() {
        Appointment appointment = seedAppointment(AppointmentStatus.CONFIRMED,
                Instant.now().minus(2, ChronoUnit.HOURS));

        // CONFIRMED is not reviewable yet.
        assertApiError(() -> reviewService.create(
                        appointment.getPatient().getId(),
                        new CreateReviewRequest(appointment.getId(), 5, "Not yet")),
                "APPOINTMENT_NOT_COMPLETED");

        appointment.transitionTo(AppointmentStatus.COMPLETED);
        appointmentRepository.saveAndFlush(appointment);

        ReviewResponse response = reviewService.create(
                appointment.getPatient().getId(),
                new CreateReviewRequest(appointment.getId(), 4, "  Good visit  "));

        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.comment()).isEqualTo("Good visit");
        assertThat(response.patientName()).isEqualTo("Patient D.");

        Doctor doctor = doctorRepository.findById(appointment.getDoctor().getId()).orElseThrow();
        assertThat(doctor.getRatingAverage()).isEqualByComparingTo("4.00");
        assertThat(doctor.getRatingCount()).isEqualTo(1);

        assertApiError(() -> reviewService.create(
                        appointment.getPatient().getId(),
                        new CreateReviewRequest(appointment.getId(), 5, "Again")),
                "REVIEW_ALREADY_EXISTS");

        Doctor unchanged = doctorRepository.findById(appointment.getDoctor().getId()).orElseThrow();
        assertThat(unchanged.getRatingCount()).isEqualTo(1);
        assertThat(unchanged.getRatingAverage()).isEqualByComparingTo("4.00");
    }

    private Appointment seedAppointment(AppointmentStatus status, Instant startAt) {
        String suffix = UUID.randomUUID().toString();
        User patient = userRepository.save(new User(
                "patient-" + suffix + "@example.com", "hash", Role.PATIENT, "Patient Database", null));
        Specialty specialty = specialtyRepository.save(new Specialty(
                "Specialty " + suffix, "Test specialty"));
        User doctorUser = userRepository.save(new User(
                "doctor-" + suffix + "@example.com", "hash", Role.DOCTOR, "Dr. Database", null));
        Doctor doctor = doctorRepository.save(new Doctor(
                doctorUser, specialty, null, new BigDecimal("500.00")));

        Appointment appointment = new Appointment(patient, doctor, null,
                startAt, startAt.plus(Duration.ofMinutes(30)));
        if (status == AppointmentStatus.CONFIRMED || status == AppointmentStatus.COMPLETED
                || status == AppointmentStatus.NO_SHOW) {
            appointment.transitionTo(AppointmentStatus.CONFIRMED);
        }
        if (status == AppointmentStatus.COMPLETED) {
            appointment.transitionTo(AppointmentStatus.COMPLETED);
        }
        if (status == AppointmentStatus.CANCELLED) {
            appointment.transitionTo(AppointmentStatus.CANCELLED);
        }
        if (status == AppointmentStatus.NO_SHOW) {
            appointment.transitionTo(AppointmentStatus.NO_SHOW);
        }
        return appointmentRepository.saveAndFlush(appointment);
    }

    private static void assertApiError(ThrowableAssert.ThrowingCallable call, String code) {
        ApiException ex = catchThrowableOfType(call, ApiException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(code);
    }
}

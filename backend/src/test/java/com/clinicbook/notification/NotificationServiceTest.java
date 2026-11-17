package com.clinicbook.notification;

import com.clinicbook.appointment.Appointment;
import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.config.NotificationProperties;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-05T08:00:00Z");
    private static final Duration LEAD = Duration.ofHours(24);

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private EmailService emailService;

    private NotificationService service;
    private User patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                appointmentRepository,
                notificationLogRepository,
                emailService,
                new NotificationProperties(true, "no-reply@test.local", "ClinicBook",
                        "0 0 * * * *", LEAD),
                Clock.fixed(NOW, ZoneOffset.UTC));
        patient = user(1L, Role.PATIENT, "Patient One", "patient@example.com");
        doctor = doctor(7L, "Dr. Test", "doctor@example.com");
    }

    @Test
    void sendsAReminderAndRecordsIt() {
        Appointment appointment = appointment(50L, AppointmentStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(12)));
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(notificationLogRepository.existsByAppointmentIdAndType(50L, NotificationType.REMINDER))
                .thenReturn(false);
        when(notificationLogRepository.saveAndFlush(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.sendReminder(50L)).isTrue();

        verify(emailService).send(appointment, NotificationType.REMINDER);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).saveAndFlush(captor.capture());
        NotificationLog log = captor.getValue();
        assertThat(log.getAppointment()).isSameAs(appointment);
        assertThat(log.getType()).isEqualTo(NotificationType.REMINDER);
        assertThat(log.getRecipient()).isEqualTo("patient@example.com");
        assertThat(log.getSentAt()).isEqualTo(NOW);
    }

    @Test
    void skipsAReminderThatWasAlreadySent() {
        Appointment appointment = appointment(50L, AppointmentStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(12)));
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(notificationLogRepository.existsByAppointmentIdAndType(50L, NotificationType.REMINDER))
                .thenReturn(true);

        assertThat(service.sendReminder(50L)).isFalse();

        verify(emailService, never()).send(any(), any());
        verify(notificationLogRepository, never()).saveAndFlush(any());
    }

    @Test
    void skipsAnAppointmentThatIsNotConfirmed() {
        Appointment appointment = appointment(50L, AppointmentStatus.PENDING,
                NOW.plus(Duration.ofHours(12)));
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));

        assertThat(service.sendReminder(50L)).isFalse();

        verify(emailService, never()).send(any(), any());
    }

    @Test
    void skipsAnAppointmentOutsideTheReminderWindow() {
        Appointment appointment = appointment(50L, AppointmentStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(25)));
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));

        assertThat(service.sendReminder(50L)).isFalse();

        verify(emailService, never()).send(any(), any());
    }

    @Test
    void skipsAnAppointmentThatHasAlreadyStarted() {
        Appointment appointment = appointment(50L, AppointmentStatus.CONFIRMED,
                NOW.minus(Duration.ofMinutes(1)));
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));

        assertThat(service.sendReminder(50L)).isFalse();

        verify(emailService, never()).send(any(), any());
    }

    @Test
    void doesNotRecordAReminderWhenMailDeliveryFails() {
        Appointment appointment = appointment(50L, AppointmentStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(12)));
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(notificationLogRepository.existsByAppointmentIdAndType(50L, NotificationType.REMINDER))
                .thenReturn(false);
        org.mockito.Mockito.doThrow(new MailSendException("smtp down"))
                .when(emailService).send(appointment, NotificationType.REMINDER);

        assertThatThrownBy(() -> service.sendReminder(50L))
                .isInstanceOf(MailSendException.class);

        verify(notificationLogRepository, never()).saveAndFlush(any());
    }

    @Test
    void deliversALifecycleEmailOnlyOnce() {
        Appointment appointment = appointment(50L, AppointmentStatus.PENDING,
                NOW.plus(Duration.ofDays(2)));
        when(appointmentRepository.findById(50L)).thenReturn(Optional.of(appointment));
        when(notificationLogRepository.existsByAppointmentIdAndType(50L, NotificationType.BOOKING))
                .thenReturn(false);
        when(notificationLogRepository.saveAndFlush(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.deliver(50L, NotificationType.BOOKING)).isTrue();

        verify(emailService).send(appointment, NotificationType.BOOKING);
        verify(notificationLogRepository).saveAndFlush(any());
    }

    @Test
    void skipsADuplicateLifecycleEmail() {
        Appointment appointment = appointment(50L, AppointmentStatus.PENDING,
                NOW.plus(Duration.ofDays(2)));
        when(appointmentRepository.findById(50L)).thenReturn(Optional.of(appointment));
        when(notificationLogRepository.existsByAppointmentIdAndType(50L, NotificationType.BOOKING))
                .thenReturn(true);

        assertThat(service.deliver(50L, NotificationType.BOOKING)).isFalse();

        verify(emailService, never()).send(any(), any());
    }

    @Test
    void skipsADeletedAppointment() {
        when(appointmentRepository.findById(50L)).thenReturn(Optional.empty());

        assertThat(service.deliver(50L, NotificationType.CONFIRMATION)).isFalse();

        verify(emailService, never()).send(any(), any());
    }

    private static User user(Long id, Role role, String fullName, String email) {
        User user = new User(email, "hash", role, fullName, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Doctor doctor(Long id, String fullName, String email) {
        User user = user(1_000L + id, Role.DOCTOR, fullName, email);
        Specialty specialty = new Specialty("Cardiology", "Heart care");
        ReflectionTestUtils.setField(specialty, "id", 2L);
        Doctor doctor = new Doctor(user, specialty, null, new BigDecimal("800.00"));
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }

    private Appointment appointment(Long id, AppointmentStatus status, Instant startAt) {
        Appointment appointment = new Appointment(patient, doctor, null, startAt,
                startAt.plus(Duration.ofMinutes(30)));
        ReflectionTestUtils.setField(appointment, "id", id);
        ReflectionTestUtils.setField(appointment, "status", status);
        return appointment;
    }
}

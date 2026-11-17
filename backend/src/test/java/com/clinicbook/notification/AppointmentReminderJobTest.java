package com.clinicbook.notification;

import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.config.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentReminderJobTest {

    private static final Instant NOW = Instant.parse("2026-01-05T08:00:00Z");

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private NotificationService notificationService;

    private AppointmentReminderJob job;

    @BeforeEach
    void setUp() {
        job = new AppointmentReminderJob(
                appointmentRepository,
                notificationService,
                new NotificationProperties(true, "no-reply@test.local", "ClinicBook",
                        "0 0 * * * *", Duration.ofHours(24)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sendsEveryCandidateAndContinuesAfterAFailure() {
        when(appointmentRepository.findReminderCandidates(
                AppointmentStatus.CONFIRMED, NOW, NOW.plus(Duration.ofHours(24))))
                .thenReturn(List.of(50L, 51L));
        when(notificationService.sendReminder(50L)).thenReturn(true);
        when(notificationService.sendReminder(51L)).thenThrow(new MailSendException("smtp down"));

        job.sendReminders();

        verify(notificationService).sendReminder(50L);
        verify(notificationService).sendReminder(51L);
    }

    @Test
    void doesNothingWhenNotificationsAreDisabled() {
        AppointmentReminderJob disabled = new AppointmentReminderJob(
                appointmentRepository,
                notificationService,
                new NotificationProperties(false, "no-reply@test.local", "ClinicBook",
                        "0 0 * * * *", Duration.ofHours(24)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        disabled.sendReminders();

        verify(appointmentRepository, never()).findReminderCandidates(any(), any(), any());
        verify(notificationService, never()).sendReminder(any());
    }
}

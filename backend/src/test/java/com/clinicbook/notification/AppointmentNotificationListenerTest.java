package com.clinicbook.notification;

import com.clinicbook.appointment.event.AppointmentBookedEvent;
import com.clinicbook.appointment.event.AppointmentCancelledEvent;
import com.clinicbook.appointment.event.AppointmentConfirmedEvent;
import com.clinicbook.config.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    private AppointmentNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new AppointmentNotificationListener(notificationService,
                new NotificationProperties(true, "no-reply@test.local", "ClinicBook",
                        "0 0 * * * *", Duration.ofHours(24)));
    }

    @Test
    void mapsEachEventToItsEmailType() {
        listener.onBooked(new AppointmentBookedEvent(50L));
        listener.onConfirmed(new AppointmentConfirmedEvent(51L));
        listener.onCancelled(new AppointmentCancelledEvent(52L));

        verify(notificationService).deliver(50L, NotificationType.BOOKING);
        verify(notificationService).deliver(51L, NotificationType.CONFIRMATION);
        verify(notificationService).deliver(52L, NotificationType.CANCELLATION);
    }

    @Test
    void swallowsMailFailuresSoTheAppointmentActionStillSucceeds() {
        when(notificationService.deliver(50L, NotificationType.BOOKING))
                .thenThrow(new MailSendException("smtp down"));

        assertThatCode(() -> listener.onBooked(new AppointmentBookedEvent(50L)))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenNotificationsAreDisabled() {
        AppointmentNotificationListener disabled = new AppointmentNotificationListener(
                notificationService,
                new NotificationProperties(false, "no-reply@test.local", "ClinicBook",
                        "0 0 * * * *", Duration.ofHours(24)));

        disabled.onBooked(new AppointmentBookedEvent(50L));

        verify(notificationService, never()).deliver(50L, NotificationType.BOOKING);
    }
}

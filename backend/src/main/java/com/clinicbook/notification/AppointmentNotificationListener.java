package com.clinicbook.notification;

import com.clinicbook.appointment.event.AppointmentBookedEvent;
import com.clinicbook.appointment.event.AppointmentCancelledEvent;
import com.clinicbook.appointment.event.AppointmentConfirmedEvent;
import com.clinicbook.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns committed appointment events into best-effort emails. Any failure is logged and
 * swallowed so a broken SMTP server can never turn a successful booking into an error.
 */
@Component
public class AppointmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);

    private final NotificationService notificationService;
    private final NotificationProperties properties;

    public AppointmentNotificationListener(NotificationService notificationService,
                                           NotificationProperties properties) {
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBooked(AppointmentBookedEvent event) {
        deliver(event.appointmentId(), NotificationType.BOOKING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(AppointmentConfirmedEvent event) {
        deliver(event.appointmentId(), NotificationType.CONFIRMATION);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(AppointmentCancelledEvent event) {
        deliver(event.appointmentId(), NotificationType.CANCELLATION);
    }

    private void deliver(Long appointmentId, NotificationType type) {
        if (!properties.enabled()) {
            return;
        }
        try {
            notificationService.deliver(appointmentId, type);
        } catch (RuntimeException ex) {
            log.warn("Could not send {} email for appointment {}: {}",
                    type, appointmentId, ex.getMessage());
        }
    }
}

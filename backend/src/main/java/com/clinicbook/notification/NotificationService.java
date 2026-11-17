package com.clinicbook.notification;

import com.clinicbook.appointment.Appointment;
import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Delivers notifications in their own transaction. Keeping this boundary separate from
 * the booking transaction means an SMTP failure rolls back only the send, never the
 * appointment, and it also keeps {@code notification_log} consistent with what was sent.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final AppointmentRepository appointmentRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationService(AppointmentRepository appointmentRepository,
                               NotificationLogRepository notificationLogRepository,
                               EmailService emailService,
                               NotificationProperties properties,
                               Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.emailService = emailService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Sends one lifecycle email. Returns {@code false} when the appointment is gone or
     * the email was already delivered.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(Long appointmentId, NotificationType type) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            log.warn("Skipping {} email: appointment {} no longer exists", type, appointmentId);
            return false;
        }
        if (notificationLogRepository.existsByAppointmentIdAndType(appointmentId, type)) {
            return false;
        }

        emailService.send(appointment, type);
        notificationLogRepository.saveAndFlush(new NotificationLog(
                appointment, type, appointment.getPatient().getEmail(), clock.instant()));
        return true;
    }

    /**
     * Sends the 24-hour reminder for one confirmed appointment. The appointment row is
     * locked, the window and status are re-checked, and {@code notification_log} is
     * consulted before sending, so concurrent or repeated job runs send exactly once.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendReminder(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId).orElse(null);
        if (appointment == null || appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            return false;
        }

        Instant now = clock.instant();
        Instant start = appointment.getStartAt();
        if (!start.isAfter(now) || start.isAfter(now.plus(properties.reminderLead()))) {
            return false;
        }
        if (notificationLogRepository.existsByAppointmentIdAndType(appointmentId, NotificationType.REMINDER)) {
            return false;
        }

        emailService.send(appointment, NotificationType.REMINDER);
        notificationLogRepository.saveAndFlush(new NotificationLog(
                appointment, NotificationType.REMINDER,
                appointment.getPatient().getEmail(), now));
        return true;
    }
}

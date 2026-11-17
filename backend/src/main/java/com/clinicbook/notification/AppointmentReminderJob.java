package com.clinicbook.notification;

import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Hourly reminder sweep. Each appointment is handled in its own transaction by
 * {@link NotificationService#sendReminder(Long)}, so one SMTP failure cannot stop the
 * rest of the run.
 */
@Component
public class AppointmentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderJob.class);

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final NotificationProperties properties;
    private final Clock clock;

    public AppointmentReminderJob(AppointmentRepository appointmentRepository,
                                  NotificationService notificationService,
                                  NotificationProperties properties,
                                  Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notifications.reminder-cron:0 0 * * * *}", zone = "${app.clinic.zone:UTC}")
    public void sendReminders() {
        if (!properties.enabled()) {
            return;
        }

        Instant now = clock.instant();
        List<Long> candidates = appointmentRepository.findReminderCandidates(
                AppointmentStatus.CONFIRMED, now, now.plus(properties.reminderLead()));

        int sent = 0;
        for (Long appointmentId : candidates) {
            try {
                if (notificationService.sendReminder(appointmentId)) {
                    sent++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not send reminder for appointment {}: {}",
                        appointmentId, ex.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Sent {} appointment reminder(s)", sent);
        }
    }
}

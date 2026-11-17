package com.clinicbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Notification settings. Emails are best-effort: a mail failure never rolls back a
 * booking or appointment state change.
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        boolean enabled,
        String from,
        String clinicName,
        String reminderCron,
        Duration reminderLead) {

    public NotificationProperties {
        if (from == null || from.isBlank()) {
            from = "no-reply@clinicbook.local";
        }
        if (clinicName == null || clinicName.isBlank()) {
            clinicName = "ClinicBook";
        }
        if (reminderCron == null || reminderCron.isBlank()) {
            reminderCron = "0 0 * * * *";
        }
        if (reminderLead == null || reminderLead.isZero() || reminderLead.isNegative()) {
            reminderLead = Duration.ofHours(24);
        }
    }
}

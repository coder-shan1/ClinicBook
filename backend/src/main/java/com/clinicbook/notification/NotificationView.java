package com.clinicbook.notification;

/**
 * Values already formatted for display in an email template.
 */
public record NotificationView(
        Long appointmentId,
        String patientName,
        String doctorName,
        String specialty,
        String startsAt,
        String clinicName) {
}

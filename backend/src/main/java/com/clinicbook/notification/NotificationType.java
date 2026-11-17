package com.clinicbook.notification;

/**
 * Kinds of appointment email. Each type is sent at most once per appointment.
 */
public enum NotificationType {
    BOOKING,
    CONFIRMATION,
    CANCELLATION,
    REMINDER
}

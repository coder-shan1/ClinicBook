package com.clinicbook.appointment;

/**
 * Appointment lifecycle. Terminal states never move again; cancelled appointments keep
 * their history while their slot is released for someone else.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }

    /** Statuses that count against a patient's active-booking limit. */
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean canTransitionTo(AppointmentStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == COMPLETED || target == CANCELLED || target == NO_SHOW;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}

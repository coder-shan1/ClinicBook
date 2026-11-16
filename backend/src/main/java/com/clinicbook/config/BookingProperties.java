package com.clinicbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Booking rules that patients cannot bypass. Staff actions (doctor/admin) are not
 * limited by the cancellation cutoff.
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(Duration cancelCutoff, int maxActivePerPatient) {

    public BookingProperties {
        if (cancelCutoff == null || cancelCutoff.isNegative()) {
            cancelCutoff = Duration.ofHours(2);
        }
        if (maxActivePerPatient <= 0) {
            maxActivePerPatient = 3;
        }
    }
}

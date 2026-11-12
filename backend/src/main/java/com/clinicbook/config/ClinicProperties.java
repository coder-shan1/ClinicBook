package com.clinicbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Clinic-local settings. Working hours are wall-clock times in this zone; slots are
 * always stored as UTC instants.
 */
@ConfigurationProperties(prefix = "app.clinic")
public record ClinicProperties(ZoneId zone, int slotHorizonDays) {

    public ClinicProperties {
        if (zone == null) {
            zone = ZoneId.of("UTC");
        }
        if (slotHorizonDays <= 0) {
            slotHorizonDays = 30;
        }
    }
}

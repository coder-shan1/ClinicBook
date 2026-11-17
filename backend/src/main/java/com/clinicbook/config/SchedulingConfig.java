package com.clinicbook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the nightly slot-extension job and the appointment reminder job.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ClinicProperties.class, BookingProperties.class,
        NotificationProperties.class})
public class SchedulingConfig {
}

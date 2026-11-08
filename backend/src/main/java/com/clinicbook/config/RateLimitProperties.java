package com.clinicbook.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(@Valid Login login) {

    public record Login(@Min(1) int maxAttempts, @NotNull Duration window) {
    }
}

package com.clinicbook.schedule.dto;

import java.time.Instant;

public record SlotGenerationResponse(
        Instant generatedAt,
        int created,
        int removed,
        int resized,
        int available) {
}

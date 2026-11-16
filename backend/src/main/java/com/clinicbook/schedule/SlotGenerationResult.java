package com.clinicbook.schedule;

import java.time.Instant;

public record SlotGenerationResult(
        Instant generatedAt,
        int created,
        int removed,
        int resized,
        int available) {
}

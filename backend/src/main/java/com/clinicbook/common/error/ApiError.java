package com.clinicbook.common.error;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every endpoint, as defined in ARCHITECTURE.md §7.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldValidationError> fieldErrors) {
}

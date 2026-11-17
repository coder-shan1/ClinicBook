package com.clinicbook.review.dto;

import com.clinicbook.review.Review;

import java.time.Instant;

/**
 * Public review shape. The patient's full name is masked so public doctor pages do not
 * expose who left each review.
 */
public record ReviewResponse(
        Long id,
        Long appointmentId,
        Long doctorId,
        String patientName,
        int rating,
        String comment,
        Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getAppointment().getId(),
                review.getDoctor().getId(),
                maskName(review.getPatient().getFullName()),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }

    static String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Patient";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}

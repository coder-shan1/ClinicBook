package com.clinicbook.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminStatsResponse(
        LocalDate from,
        LocalDate to,
        long totalBookings,
        long pendingBookings,
        long confirmedBookings,
        long completedBookings,
        long cancelledBookings,
        long noShowBookings,
        BigDecimal cancellationRate,
        List<DailyBookings> bookingsPerDay,
        List<SpecialtyBookings> bookingsPerSpecialty,
        List<TopDoctor> topDoctors) {

    public record DailyBookings(LocalDate date, long bookings) {
    }

    public record SpecialtyBookings(Long specialtyId, String specialtyName, long bookings) {
    }

    public record TopDoctor(
            Long doctorId,
            String doctorName,
            String specialty,
            long bookings,
            long completed,
            BigDecimal ratingAverage,
            int ratingCount) {
    }
}

package com.clinicbook.admin;

import com.clinicbook.admin.dto.AdminStatsResponse;
import com.clinicbook.appointment.Appointment;
import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.config.ClinicProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates appointment statistics in Java using the clinic timezone. Keeping the
 * grouping out of SQL avoids dialect differences between MySQL and PostgreSQL.
 */
@Service
public class AdminStatsService {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 366;
    private static final int MAX_TOP_DOCTORS = 20;

    private final AppointmentRepository appointmentRepository;
    private final ClinicProperties clinicProperties;
    private final Clock clock;

    public AdminStatsService(AppointmentRepository appointmentRepository,
                             ClinicProperties clinicProperties,
                             Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clinicProperties = clinicProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats(LocalDate from, LocalDate to, int topDoctorLimit) {
        ZoneId zone = clinicProperties.zone();
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS - 1L);

        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "The start date must not be after the end date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_WINDOW_DAYS - 1L) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_TOO_LARGE",
                    "The date range must not exceed " + MAX_WINDOW_DAYS + " days");
        }

        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> appointments =
                appointmentRepository.findAllByStartAtGreaterThanEqualAndStartAtLessThan(
                        fromInstant, toInstant);

        long pending = 0;
        long confirmed = 0;
        long completed = 0;
        long cancelled = 0;
        long noShow = 0;

        Map<LocalDate, Long> daily = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            daily.put(day, 0L);
        }

        Map<Long, SpecialtyTotals> specialties = new LinkedHashMap<>();
        Map<Long, DoctorTotals> doctors = new LinkedHashMap<>();

        for (Appointment appointment : appointments) {
            switch (appointment.getStatus()) {
                case PENDING -> pending++;
                case CONFIRMED -> confirmed++;
                case COMPLETED -> completed++;
                case CANCELLED -> cancelled++;
                case NO_SHOW -> noShow++;
            }

            LocalDate day = appointment.getStartAt().atZone(zone).toLocalDate();
            daily.merge(day, 1L, Long::sum);

            Long specialtyId = appointment.getDoctor().getSpecialty().getId();
            specialties.computeIfAbsent(specialtyId, id -> new SpecialtyTotals(
                            id, appointment.getDoctor().getSpecialty().getName()))
                    .increment();

            Long doctorId = appointment.getDoctor().getId();
            DoctorTotals totals = doctors.computeIfAbsent(doctorId, id -> new DoctorTotals(
                    id,
                    appointment.getDoctor().getUser().getFullName(),
                    appointment.getDoctor().getSpecialty().getName(),
                    appointment.getDoctor().getRatingAverage(),
                    appointment.getDoctor().getRatingCount()));
            totals.bookings++;
            if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
                totals.completed++;
            }
        }

        long total = appointments.size();
        BigDecimal cancellationRate = total == 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(cancelled)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);

        int limit = Math.min(Math.max(topDoctorLimit, 1), MAX_TOP_DOCTORS);
        List<AdminStatsResponse.DailyBookings> perDay = daily.entrySet().stream()
                .map(entry -> new AdminStatsResponse.DailyBookings(entry.getKey(), entry.getValue()))
                .toList();

        List<AdminStatsResponse.SpecialtyBookings> perSpecialty = specialties.values().stream()
                .sorted(Comparator.comparingLong(SpecialtyTotals::bookings).reversed()
                        .thenComparing(SpecialtyTotals::name))
                .map(item -> new AdminStatsResponse.SpecialtyBookings(
                        item.id, item.name, item.bookings))
                .toList();

        List<AdminStatsResponse.TopDoctor> topDoctors = doctors.values().stream()
                .sorted(Comparator.comparingLong(DoctorTotals::bookings).reversed()
                        .thenComparing(Comparator.comparingLong(DoctorTotals::completed).reversed())
                        .thenComparing(DoctorTotals::name))
                .limit(limit)
                .map(item -> new AdminStatsResponse.TopDoctor(
                        item.id,
                        item.name,
                        item.specialty,
                        item.bookings,
                        item.completed,
                        item.ratingAverage,
                        item.ratingCount))
                .toList();

        return new AdminStatsResponse(
                start, end, total, pending, confirmed, completed, cancelled, noShow,
                cancellationRate, perDay, perSpecialty, topDoctors);
    }

    private static final class SpecialtyTotals {
        private final Long id;
        private final String name;
        private long bookings;

        private SpecialtyTotals(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private void increment() {
            bookings++;
        }

        private long bookings() {
            return bookings;
        }

        private String name() {
            return name;
        }
    }

    private static final class DoctorTotals {
        private final Long id;
        private final String name;
        private final String specialty;
        private final BigDecimal ratingAverage;
        private final int ratingCount;
        private long bookings;
        private long completed;

        private DoctorTotals(Long id, String name, String specialty,
                             BigDecimal ratingAverage, int ratingCount) {
            this.id = id;
            this.name = name;
            this.specialty = specialty;
            this.ratingAverage = ratingAverage;
            this.ratingCount = ratingCount;
        }

        private long bookings() {
            return bookings;
        }

        private long completed() {
            return completed;
        }

        private String name() {
            return name;
        }
    }
}

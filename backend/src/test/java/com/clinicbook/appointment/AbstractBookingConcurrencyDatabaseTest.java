package com.clinicbook.appointment;

import com.clinicbook.appointment.dto.CreateAppointmentRequest;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.doctor.SpecialtyRepository;
import com.clinicbook.schedule.TimeSlot;
import com.clinicbook.schedule.TimeSlotRepository;
import com.clinicbook.schedule.TimeSlotStatus;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the booking engine never double-books, even when many patients race for the
 * same slot. Subclasses supply a MySQL or PostgreSQL container; the suite skips itself
 * when Docker is absent and runs in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
abstract class AbstractBookingConcurrencyDatabaseTest {

    private static final int ATTEMPTS = 20;

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private TimeSlotRepository timeSlotRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecialtyRepository specialtyRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void exactlyOneOfTwentyConcurrentBookingsWins() throws Exception {
        TimeSlot slot = seedSlot();
        List<Long> patientIds = seedPatients(ATTEMPTS);

        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (Long patientId : patientIds) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        appointmentService.book(patientId, new CreateAppointmentRequest(slot.getId()));
                        return true;
                    } catch (ApiException ex) {
                        assertThat(ex.getCode())
                                .as("losing bookings must fail with SLOT_TAKEN")
                                .isEqualTo("SLOT_TAKEN");
                        return false;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(60, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(appointmentRepository.count()).isEqualTo(1);
        assertThat(timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus())
                .isEqualTo(TimeSlotStatus.BOOKED);
        assertThat(appointmentRepository.findAll())
                .singleElement()
                .satisfies(appointment -> {
                    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
                    assertThat(appointment.getSlot().getId()).isEqualTo(slot.getId());
                });
    }

    private TimeSlot seedSlot() {
        Specialty specialty = specialtyRepository.save(new Specialty("Concurrency", "Booking race tests"));
        User doctorUser = userRepository.save(
                new User("concurrency-doctor@example.com", "hash", Role.DOCTOR, "Dr. Concurrent", null));
        Doctor doctor = doctorRepository.save(
                new Doctor(doctorUser, specialty, null, new BigDecimal("500.00")));
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        return timeSlotRepository.save(new TimeSlot(doctor, start, start.plus(Duration.ofMinutes(30))));
    }

    private List<Long> seedPatients(int count) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            User patient = userRepository.save(new User(
                    "concurrency-patient-" + i + "@example.com", "hash", Role.PATIENT,
                    "Concurrent Patient " + i, null));
            ids.add(patient.getId());
        }
        return ids;
    }
}

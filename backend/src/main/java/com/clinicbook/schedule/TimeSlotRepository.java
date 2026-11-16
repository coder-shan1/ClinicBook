package com.clinicbook.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    List<TimeSlot> findAllByDoctorIdAndStartAtBetweenOrderByStartAtAsc(Long doctorId, Instant from, Instant to);

    List<TimeSlot> findAllByDoctorIdAndStatusAndStartAtBetweenOrderByStartAtAsc(
            Long doctorId, TimeSlotStatus status, Instant from, Instant to);

    Optional<TimeSlot> findByDoctorIdAndStartAt(Long doctorId, Instant startAt);

    boolean existsByDoctorIdAndStartAt(Long doctorId, Instant startAt);
}

package com.clinicbook.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

    List<TimeOff> findAllByDoctorIdOrderByStartAtAsc(Long doctorId);

    List<TimeOff> findAllByDoctorIdAndEndAtAfterAndStartAtBefore(Long doctorId, Instant from, Instant to);

    boolean existsByDoctorIdAndEndAtAfterAndStartAtBefore(Long doctorId, Instant from, Instant to);

    Optional<TimeOff> findByIdAndDoctorId(Long id, Long doctorId);
}

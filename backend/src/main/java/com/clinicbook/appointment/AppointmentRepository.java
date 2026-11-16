package com.clinicbook.appointment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Row lock used by state changes. Kept separate from the entity-graph query because
     * PostgreSQL rejects {@code FOR UPDATE} on the nullable side of an outer join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"patient", "slot", "doctor", "doctor.user", "doctor.specialty"})
    Page<Appointment> findAllByPatientId(Long patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "slot", "doctor", "doctor.user", "doctor.specialty"})
    Page<Appointment> findAllByDoctorId(Long doctorId, Pageable pageable);

    long countByPatientIdAndStatusIn(Long patientId, Collection<AppointmentStatus> statuses);
}


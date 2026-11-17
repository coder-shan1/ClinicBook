package com.clinicbook.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByAppointmentId(Long appointmentId);

    @EntityGraph(attributePaths = {"patient"})
    Page<Review> findAllByDoctorId(Long doctorId, Pageable pageable);
}

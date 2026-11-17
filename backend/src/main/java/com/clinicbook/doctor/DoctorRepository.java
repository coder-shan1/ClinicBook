package com.clinicbook.doctor;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @EntityGraph(attributePaths = {"user", "specialty"})
    Optional<Doctor> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "specialty"})
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findWithProfileById(@Param("id") Long id);

    /**
     * Serialises rating-aggregate updates for one doctor. Reviews for different doctors
     * still proceed in parallel.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "specialty"})
    @Override
    Page<Doctor> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "specialty"})
    List<Doctor> findAllByActiveTrue();

    boolean existsByUserId(Long userId);

    boolean existsBySpecialtyId(Long specialtyId);

    @EntityGraph(attributePaths = {"user", "specialty"})
    @Query("""
            select d from Doctor d
            where d.active = true
              and (:specialtyId is null or d.specialty.id = :specialtyId)
              and (lower(d.user.fullName) like concat('%', lower(:q), '%')
                   or lower(d.specialty.name) like concat('%', lower(:q), '%'))
            """)
    Page<Doctor> search(@Param("specialtyId") Long specialtyId,
                        @Param("q") String q,
                        Pageable pageable);
}

package com.clinicbook.doctor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
              and (:q is null
                   or lower(d.user.fullName) like lower(concat('%', :q, '%'))
                   or lower(d.specialty.name) like lower(concat('%', :q, '%')))
            """)
    Page<Doctor> search(@Param("specialtyId") Long specialtyId,
                        @Param("q") String q,
                        Pageable pageable);
}

package com.clinicbook.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Serialises booking-limit checks for one patient. Concurrent bookings by different
     * patients lock different rows, so the slot itself stays the serialisation point.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    long countByRoleAndEnabledTrue(Role role);

    @Query("""
            select u from User u
            where lower(u.email) like concat('%', :q, '%')
               or lower(u.fullName) like concat('%', :q, '%')
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);

    @Query("""
            select u from User u
            where u.role = :role
              and (lower(u.email) like concat('%', :q, '%')
                   or lower(u.fullName) like concat('%', :q, '%'))
            """)
    Page<User> searchByRole(@Param("role") Role role, @Param("q") String q, Pageable pageable);
}

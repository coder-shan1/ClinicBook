package com.clinicbook.doctor;

import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the public doctor-search query against a real database. Subclasses supply a
 * MySQL or PostgreSQL container; the test suite skips itself when Docker is absent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
abstract class AbstractDoctorSearchDatabaseTest {

    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecialtyRepository specialtyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private Long cardiologyId;

    @BeforeEach
    void seedDoctors() {
        Specialty cardiology = specialtyRepository.save(new Specialty("Cardiology", "Heart care"));
        Specialty dermatology = specialtyRepository.save(new Specialty("Dermatology", "Skin care"));
        cardiologyId = cardiology.getId();

        doctorRepository.save(doctor("Alice Sharma", "alice@example.com", cardiology, "800.00", true));
        doctorRepository.save(doctor("Bob Iyer", "bob@example.com", dermatology, "600.00", true));
        doctorRepository.save(doctor("Carol Nair", "carol@example.com", cardiology, "900.00", true));
        doctorRepository.save(doctor("Dave Rao", "dave@example.com", cardiology, "500.00", false));
        entityManager.flush();
    }

    @Test
    void searchReturnsOnlyActiveDoctorsSortedByName() {
        Page<Doctor> page = doctorRepository.search(null, null,
                PageRequest.of(0, 10, Sort.by("user.fullName")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .extracting(doctor -> doctor.getUser().getFullName())
                .containsExactly("Alice Sharma", "Bob Iyer", "Carol Nair");
    }

    @Test
    void searchFiltersBySpecialty() {
        Page<Doctor> page = doctorRepository.search(cardiologyId, null,
                PageRequest.of(0, 10, Sort.by("user.fullName")));

        assertThat(page.getContent())
                .extracting(doctor -> doctor.getUser().getFullName())
                .containsExactly("Alice Sharma", "Carol Nair");
    }

    @Test
    void searchMatchesDoctorNameCaseInsensitively() {
        Page<Doctor> page = doctorRepository.search(null, "ALI",
                PageRequest.of(0, 10, Sort.by("user.fullName")));

        assertThat(page.getContent())
                .extracting(doctor -> doctor.getUser().getFullName())
                .containsExactly("Alice Sharma");
    }

    @Test
    void searchMatchesSpecialtyName() {
        Page<Doctor> page = doctorRepository.search(null, "derma",
                PageRequest.of(0, 10, Sort.by("user.fullName")));

        assertThat(page.getContent())
                .extracting(doctor -> doctor.getUser().getFullName())
                .containsExactly("Bob Iyer");
    }

    @Test
    void searchCombinesSpecialtyAndQuery() {
        Page<Doctor> page = doctorRepository.search(cardiologyId, "car",
                PageRequest.of(0, 10, Sort.by("user.fullName")));

        assertThat(page.getContent())
                .extracting(doctor -> doctor.getUser().getFullName())
                .containsExactly("Carol Nair");
    }

    @Test
    void searchPaginatesAndReportsTotals() {
        Page<Doctor> first = doctorRepository.search(null, null,
                PageRequest.of(0, 2, Sort.by("user.fullName")));
        Page<Doctor> second = doctorRepository.search(null, null,
                PageRequest.of(1, 2, Sort.by("user.fullName")));

        assertThat(first.getContent())
                .extracting(doctor -> doctor.getUser().getFullName())
                .containsExactly("Alice Sharma", "Bob Iyer");
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent())
                .extracting(doctor -> doctor.getUser().getFullName())
                .containsExactly("Carol Nair");
    }

    @Test
    void searchSortsByConsultationFeeDescending() {
        Page<Doctor> page = doctorRepository.search(null, null,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("consultationFee"))));

        assertThat(page.getContent())
                .extracting(doctor -> doctor.getConsultationFee())
                .containsExactly(new BigDecimal("900.00"), new BigDecimal("800.00"), new BigDecimal("600.00"));
    }

    private Doctor doctor(String fullName, String email, Specialty specialty,
                          String fee, boolean active) {
        User user = userRepository.save(
                new User(email, "hash", Role.DOCTOR, fullName, null));
        Doctor doctor = new Doctor(user, specialty, null, new BigDecimal(fee));
        if (!active) {
            doctor.deactivate();
        }
        return doctor;
    }
}

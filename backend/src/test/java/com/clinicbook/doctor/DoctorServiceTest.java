package com.clinicbook.doctor;

import com.clinicbook.common.error.ApiException;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.config.ClinicProperties;
import com.clinicbook.doctor.dto.CreateDoctorRequest;
import com.clinicbook.doctor.dto.DoctorResponse;
import com.clinicbook.doctor.dto.DoctorSummaryResponse;
import com.clinicbook.doctor.dto.UpdateDoctorProfileRequest;
import com.clinicbook.doctor.dto.UpdateDoctorRequest;
import com.clinicbook.schedule.SlotGenerationService;
import com.clinicbook.schedule.TimeSlot;
import com.clinicbook.schedule.TimeSlotRepository;
import com.clinicbook.schedule.TimeSlotStatus;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TimeSlotRepository timeSlotRepository;
    @Mock
    private SlotGenerationService slotGenerationService;

    private Doctor doctor;
    private User doctorUser;
    private Specialty specialty;
    private DoctorService doctorService;

    @BeforeEach
    void setUp() {
        doctorUser = new User("doctor@example.com", "hash", Role.DOCTOR, "Dr. Test", "+91 90000 00001");
        ReflectionTestUtils.setField(doctorUser, "id", 7L);
        specialty = new Specialty("Cardiology", "Heart care");
        ReflectionTestUtils.setField(specialty, "id", 2L);
        doctor = new Doctor(doctorUser, specialty, "Bio", new BigDecimal("800.00"));
        ReflectionTestUtils.setField(doctor, "id", 1L);

        doctorService = new DoctorService(doctorRepository, userRepository, specialtyRepository,
                passwordEncoder, timeSlotRepository, slotGenerationService,
                new ClinicProperties(ZoneId.of("UTC"), 30),
                Clock.fixed(Instant.parse("2026-01-05T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void searchNormalisesTheQueryAndAppliesSort() {
        Page<Doctor> page = new PageImpl<>(List.of(doctor));
        when(doctorRepository.search(eq(2L), eq("heart"), any(Pageable.class))).thenReturn(page);

        PageResponse<DoctorSummaryResponse> response =
                doctorService.search(2L, "  heart  ", 1, 10, "consultationFee,desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(doctorRepository).search(eq(2L), eq("heart"), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("consultationFee")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("consultationFee").isDescending()).isTrue();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).fullName()).isEqualTo("Dr. Test");
    }

    @Test
    void searchClampsUnsafePaginationAndIgnoresUnknownSortFields() {
        when(doctorRepository.search(isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        doctorService.search(null, "   ", -5, 500, "password,desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(doctorRepository).search(isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("user.fullName")).isNotNull();
    }

    @Test
    void publicLookupRejectsAnInactiveDoctor() {
        doctor.deactivate();
        when(doctorRepository.findWithProfileById(1L)).thenReturn(Optional.of(doctor));

        assertApiError(() -> doctorService.getPublic(1L), HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND");
    }

    @Test
    void createNormalisesEmailAndCreatesDoctorRole() {
        when(userRepository.existsByEmail("doctor@example.com")).thenReturn(false);
        when(specialtyRepository.findById(2L)).thenReturn(Optional.of(specialty));
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        DoctorResponse response = doctorService.create(new CreateDoctorRequest(
                " Doctor@Example.COM ", "password123", " Dr. New ", "  ",
                2L, "Bio", new BigDecimal("650.00")));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("doctor@example.com");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.DOCTOR);
        assertThat(userCaptor.getValue().getFullName()).isEqualTo("Dr. New");
        assertThat(userCaptor.getValue().getPhone()).isNull();
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.specialty().id()).isEqualTo(2L);
    }

    @Test
    void createRejectsAnExistingEmailBeforeTouchingTheSpecialty() {
        when(userRepository.existsByEmail("doctor@example.com")).thenReturn(true);

        assertApiError(() -> doctorService.create(new CreateDoctorRequest(
                        "doctor@example.com", "password123", "Dr. New", null,
                        2L, null, new BigDecimal("650.00"))),
                HttpStatus.CONFLICT, "EMAIL_TAKEN");

        verify(specialtyRepository, never()).findById(any());
    }

    @Test
    void createRejectsAnUnknownSpecialty() {
        when(userRepository.existsByEmail("doctor@example.com")).thenReturn(false);
        when(specialtyRepository.findById(2L)).thenReturn(Optional.empty());

        assertApiError(() -> doctorService.create(new CreateDoctorRequest(
                        "doctor@example.com", "password123", "Dr. New", null,
                        2L, null, new BigDecimal("650.00"))),
                HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void adminUpdateDeactivatesTheDoctorAndDisablesTheAccount() {
        when(doctorRepository.findWithProfileById(1L)).thenReturn(Optional.of(doctor));
        when(specialtyRepository.findById(2L)).thenReturn(Optional.of(specialty));

        DoctorResponse response = doctorService.update(1L, new UpdateDoctorRequest(
                "Dr. Test", null, 2L, "Bio", new BigDecimal("900.00"), false));

        assertThat(response.active()).isFalse();
        assertThat(doctorUser.isEnabled()).isFalse();
        verify(slotGenerationService, never()).regenerate(1L);
    }

    @Test
    void adminUpdateReactivatesAndRegeneratesSlots() {
        doctor.deactivate();
        doctorUser.disable();
        when(doctorRepository.findWithProfileById(1L)).thenReturn(Optional.of(doctor));
        when(specialtyRepository.findById(2L)).thenReturn(Optional.of(specialty));

        DoctorResponse response = doctorService.update(1L, new UpdateDoctorRequest(
                "Dr. Test", null, 2L, "Bio", new BigDecimal("900.00"), true));

        assertThat(response.active()).isTrue();
        assertThat(doctorUser.isEnabled()).isTrue();
        verify(slotGenerationService).regenerate(1L);
    }

    @Test
    void updateProfileChangesSpecialtyAndFee() {
        Specialty newSpecialty = new Specialty("Dermatology", null);
        ReflectionTestUtils.setField(newSpecialty, "id", 3L);
        when(doctorRepository.findByUserId(7L)).thenReturn(Optional.of(doctor));
        when(specialtyRepository.findById(3L)).thenReturn(Optional.of(newSpecialty));

        DoctorResponse response = doctorService.updateProfile(7L,
                new UpdateDoctorProfileRequest(3L, "New bio", new BigDecimal("700.00")));

        assertThat(response.specialty().name()).isEqualTo("Dermatology");
        assertThat(response.bio()).isEqualTo("New bio");
        assertThat(response.consultationFee()).isEqualByComparingTo("700.00");
    }

    @Test
    void slotsUseClinicDayBoundaries() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        Instant from = Instant.parse("2026-01-05T00:00:00Z");
        Instant to = Instant.parse("2026-01-06T00:00:00Z");
        when(timeSlotRepository.findAllByDoctorIdAndStatusAndStartAtBetweenOrderByStartAtAsc(
                1L, TimeSlotStatus.AVAILABLE, from, to))
                .thenReturn(List.of(new TimeSlot(doctor, from.plusSeconds(3600),
                        from.plusSeconds(5400))));

        var slots = doctorService.slots(1L, LocalDate.of(2026, 1, 5));

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).status()).isEqualTo(TimeSlotStatus.AVAILABLE);
    }

    @Test
    void meRejectsAUserWithoutADoctorProfile() {
        when(doctorRepository.findByUserId(7L)).thenReturn(Optional.empty());

        assertApiError(() -> doctorService.me(7L), HttpStatus.NOT_FOUND, "DOCTOR_PROFILE_NOT_FOUND");
    }

    private static void assertApiError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                       HttpStatus status, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }
}

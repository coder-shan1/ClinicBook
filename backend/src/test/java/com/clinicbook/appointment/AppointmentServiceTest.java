package com.clinicbook.appointment;

import com.clinicbook.appointment.dto.AppointmentResponse;
import com.clinicbook.appointment.dto.CreateAppointmentRequest;
import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.config.BookingProperties;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.schedule.TimeSlot;
import com.clinicbook.schedule.TimeSlotRepository;
import com.clinicbook.schedule.TimeSlotStatus;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-05T08:00:00Z");
    private static final Duration CUTOFF = Duration.ofHours(2);
    private static final int MAX_ACTIVE = 3;

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private TimeSlotRepository timeSlotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorRepository doctorRepository;

    private AppointmentService service;
    private User patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(
                appointmentRepository,
                timeSlotRepository,
                userRepository,
                doctorRepository,
                new BookingProperties(CUTOFF, MAX_ACTIVE),
                Clock.fixed(NOW, ZoneOffset.UTC));
        patient = user(1L, Role.PATIENT, "Patient One", "patient@example.com");
        doctor = doctor(7L, "Dr. Test", "doctor@example.com");
    }

    @Test
    void bookCreatesAPendingAppointmentAndReservesTheSlot() {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofHours(3)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatusIn(any(), any())).thenReturn(0L);
        when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.saveAndFlush(slot)).thenReturn(slot);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse response = service.book(1L, new CreateAppointmentRequest(10L));

        assertThat(response.status()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(response.patientId()).isEqualTo(1L);
        assertThat(response.doctorId()).isEqualTo(7L);
        assertThat(response.slotId()).isEqualTo(10L);
        assertThat(response.startAt()).isEqualTo(slot.getStartAt());
        assertThat(slot.getStatus()).isEqualTo(TimeSlotStatus.BOOKED);

        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(saved.getValue().getSlot()).isSameAs(slot);
    }

    @Test
    void bookRejectsAnUnknownPatient() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertApiError(() -> service.book(1L, new CreateAppointmentRequest(10L)),
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void bookRejectsAPatientAtTheActiveLimit() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatusIn(any(), any())).thenReturn((long) MAX_ACTIVE);

        assertApiError(() -> service.book(1L, new CreateAppointmentRequest(10L)),
                HttpStatus.CONFLICT, "BOOKING_LIMIT_REACHED");
        verify(timeSlotRepository, never()).findById(any());
    }

    @Test
    void bookRejectsAnUnknownSlot() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatusIn(any(), any())).thenReturn(0L);
        when(timeSlotRepository.findById(10L)).thenReturn(Optional.empty());

        assertApiError(() -> service.book(1L, new CreateAppointmentRequest(10L)),
                HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND");
    }

    @Test
    void bookRejectsASlotInThePast() {
        TimeSlot slot = slot(10L, doctor, NOW.minus(Duration.ofMinutes(5)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatusIn(any(), any())).thenReturn(0L);
        when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(slot));

        assertApiError(() -> service.book(1L, new CreateAppointmentRequest(10L)),
                HttpStatus.BAD_REQUEST, "SLOT_IN_PAST");
    }

    @Test
    void bookRejectsAnInactiveDoctor() {
        doctor.deactivate();
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofHours(3)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatusIn(any(), any())).thenReturn(0L);
        when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(slot));

        assertApiError(() -> service.book(1L, new CreateAppointmentRequest(10L)),
                HttpStatus.CONFLICT, "DOCTOR_INACTIVE");
    }

    @Test
    void bookRejectsASlotThatIsAlreadyBooked() {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofHours(3)));
        slot.markBooked();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatusIn(any(), any())).thenReturn(0L);
        when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(slot));

        assertApiError(() -> service.book(1L, new CreateAppointmentRequest(10L)),
                HttpStatus.CONFLICT, "SLOT_TAKEN");
        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void bookMapsAnOptimisticLockFailureToSlotTaken() {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofHours(3)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatusIn(any(), any())).thenReturn(0L);
        when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.saveAndFlush(slot))
                .thenThrow(new ObjectOptimisticLockingFailureException(TimeSlot.class, 10L));

        assertApiError(() -> service.book(1L, new CreateAppointmentRequest(10L)),
                HttpStatus.CONFLICT, "SLOT_TAKEN");
    }

    @Test
    void bookMapsTheUniqueSlotConstraintToSlotTaken() {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofHours(3)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countByPatientIdAndStatusIn(any(), any())).thenReturn(0L);
        when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.saveAndFlush(slot)).thenReturn(slot);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate slot"));

        assertApiError(() -> service.book(1L, new CreateAppointmentRequest(10L)),
                HttpStatus.CONFLICT, "SLOT_TAKEN");
    }

    @ParameterizedTest(name = "{0} from {1}")
    @MethodSource("transitionCases")
    void enforcesTheStateMachineForEveryActionAndStatus(BookingAction action, AppointmentStatus from) {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofHours(3)));
        slot.markBooked();
        Appointment appointment = appointment(50L, patient, doctor, slot, from);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findByUserId(7L)).thenReturn(Optional.of(doctor));

        AppointmentStatus target = action.target();
        if (from.canTransitionTo(target)) {
            if (action == BookingAction.CANCEL) {
                when(timeSlotRepository.saveAndFlush(slot)).thenReturn(slot);
            }

            AppointmentResponse response = invoke(action, 50L, doctorPrincipal());

            assertThat(response.status()).isEqualTo(target);
            if (action == BookingAction.CANCEL) {
                assertThat(slot.getStatus()).isEqualTo(TimeSlotStatus.AVAILABLE);
                assertThat(appointment.getSlot()).isNull();
                assertThat(response.slotId()).isNull();
            }
        } else {
            assertApiError(() -> invoke(action, 50L, doctorPrincipal()),
                    HttpStatus.CONFLICT, "INVALID_APPOINTMENT_STATE");
            assertThat(appointment.getStatus()).isEqualTo(from);
            assertThat(slot.getStatus()).isEqualTo(TimeSlotStatus.BOOKED);
        }
    }

    @Test
    void patientCanCancelOutsideTheCutoffAndTheSlotIsReleased() {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofHours(4)));
        slot.markBooked();
        Appointment appointment = appointment(50L, patient, doctor, slot, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(timeSlotRepository.saveAndFlush(slot)).thenReturn(slot);

        AppointmentResponse response = service.cancel(50L, patientPrincipal());

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.slotId()).isNull();
        assertThat(appointment.getSlot()).isNull();
        assertThat(slot.getStatus()).isEqualTo(TimeSlotStatus.AVAILABLE);
    }

    @Test
    void patientCannotCancelInsideTheCutoff() {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofMinutes(90)));
        slot.markBooked();
        Appointment appointment = appointment(50L, patient, doctor, slot, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));

        assertApiError(() -> service.cancel(50L, patientPrincipal()),
                HttpStatus.CONFLICT, "CANCELLATION_WINDOW_CLOSED");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(slot.getStatus()).isEqualTo(TimeSlotStatus.BOOKED);
        verify(timeSlotRepository, never()).saveAndFlush(any());
    }

    @Test
    void doctorCanCancelInsideTheCutoff() {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofMinutes(30)));
        slot.markBooked();
        Appointment appointment = appointment(50L, patient, doctor, slot, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findByUserId(7L)).thenReturn(Optional.of(doctor));
        when(timeSlotRepository.saveAndFlush(slot)).thenReturn(slot);

        AppointmentResponse response = service.cancel(50L, doctorPrincipal());

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(slot.getStatus()).isEqualTo(TimeSlotStatus.AVAILABLE);
    }

    @Test
    void adminCanManageAnyAppointment() {
        TimeSlot slot = slot(10L, doctor, NOW.plus(Duration.ofMinutes(15)));
        slot.markBooked();
        Appointment appointment = appointment(50L, patient, doctor, slot, AppointmentStatus.PENDING);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(timeSlotRepository.saveAndFlush(slot)).thenReturn(slot);

        JwtPrincipal admin = new JwtPrincipal(99L, "admin@example.com", Role.ADMIN, "Admin");

        assertThat(service.cancel(50L, admin).status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void anotherPatientCannotSeeOrCancelTheAppointment() {
        Appointment appointment = appointment(50L, patient, doctor,
                slot(10L, doctor, NOW.plus(Duration.ofHours(4))), AppointmentStatus.PENDING);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));

        JwtPrincipal other = new JwtPrincipal(2L, "other@example.com", Role.PATIENT, "Other");
        assertApiError(() -> service.confirm(50L, other),
                HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND");
    }

    @Test
    void anotherDoctorCannotSeeOrCancelTheAppointment() {
        Doctor other = doctor(8L, "Dr. Other", "other-doctor@example.com");
        Appointment appointment = appointment(50L, patient, doctor,
                slot(10L, doctor, NOW.plus(Duration.ofHours(4))), AppointmentStatus.PENDING);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findByUserId(8L)).thenReturn(Optional.of(other));

        JwtPrincipal otherPrincipal = new JwtPrincipal(8L, "other-doctor@example.com", Role.DOCTOR, "Dr. Other");
        assertApiError(() -> service.confirm(50L, otherPrincipal),
                HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND");
    }

    @Test
    void doctorWithoutAProfileCannotManageAppointments() {
        Appointment appointment = appointment(50L, patient, doctor,
                slot(10L, doctor, NOW.plus(Duration.ofHours(4))), AppointmentStatus.PENDING);
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findByUserId(8L)).thenReturn(Optional.empty());

        JwtPrincipal principal = new JwtPrincipal(8L, "no-profile@example.com", Role.DOCTOR, "No Profile");
        assertApiError(() -> service.confirm(50L, principal),
                HttpStatus.NOT_FOUND, "DOCTOR_PROFILE_NOT_FOUND");
    }

    @Test
    void unknownAppointmentReturnsNotFound() {
        when(appointmentRepository.findByIdForUpdate(50L)).thenReturn(Optional.empty());

        assertApiError(() -> service.cancel(50L, patientPrincipal()),
                HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND");
    }

    @Test
    void patientListsOnlyTheirOwnAppointments() {
        Appointment appointment = appointment(50L, patient, doctor,
                slot(10L, doctor, NOW.plus(Duration.ofHours(4))), AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findAllByPatientId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appointment)));

        PageResponse<AppointmentResponse> page = service.listMine(patientPrincipal(), 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().status()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository, never()).findAllByDoctorId(any(), any());
    }

    @Test
    void doctorListsOnlyTheirOwnAppointments() {
        Appointment appointment = appointment(50L, patient, doctor,
                slot(10L, doctor, NOW.plus(Duration.ofHours(4))), AppointmentStatus.PENDING);
        when(doctorRepository.findByUserId(7L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findAllByDoctorId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appointment)));

        PageResponse<AppointmentResponse> page = service.listMine(doctorPrincipal(), 0, 20);

        assertThat(page.content()).hasSize(1);
        verify(appointmentRepository, never()).findAllByPatientId(any(), any());
    }

    @Test
    void adminHasNoPersonalAppointmentList() {
        JwtPrincipal admin = new JwtPrincipal(99L, "admin@example.com", Role.ADMIN, "Admin");

        assertApiError(() -> service.listMine(admin, 0, 20),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    private AppointmentResponse invoke(BookingAction action, Long id, JwtPrincipal principal) {
        return switch (action) {
            case CONFIRM -> service.confirm(id, principal);
            case COMPLETE -> service.complete(id, principal);
            case NO_SHOW -> service.noShow(id, principal);
            case CANCEL -> service.cancel(id, principal);
        };
    }

    private static Stream<Arguments> transitionCases() {
        List<Arguments> cases = new ArrayList<>();
        for (BookingAction action : BookingAction.values()) {
            for (AppointmentStatus status : AppointmentStatus.values()) {
                cases.add(Arguments.of(action, status));
            }
        }
        return cases.stream();
    }

    private JwtPrincipal patientPrincipal() {
        return new JwtPrincipal(1L, "patient@example.com", Role.PATIENT, "Patient One");
    }

    private JwtPrincipal doctorPrincipal() {
        return new JwtPrincipal(7L, "doctor@example.com", Role.DOCTOR, "Dr. Test");
    }

    private static void assertApiError(ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        ApiException ex = catchThrowableOfType(call, ApiException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(status);
        assertThat(ex.getCode()).isEqualTo(code);
    }

    private static User user(Long id, Role role, String fullName, String email) {
        User user = new User(email, "hash", role, fullName, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Doctor doctor(Long id, String fullName, String email) {
        User user = user(1_000L + id, Role.DOCTOR, fullName, email);
        Specialty specialty = new Specialty("Cardiology", "Heart care");
        ReflectionTestUtils.setField(specialty, "id", 2L);
        Doctor doctor = new Doctor(user, specialty, null, new BigDecimal("800.00"));
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }

    private static TimeSlot slot(Long id, Doctor doctor, Instant startAt) {
        TimeSlot slot = new TimeSlot(doctor, startAt, startAt.plus(Duration.ofMinutes(30)));
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }

    private static Appointment appointment(Long id, User patient, Doctor doctor,
                                           TimeSlot slot, AppointmentStatus status) {
        Appointment appointment = new Appointment(patient, doctor, slot, slot.getStartAt(), slot.getEndAt());
        ReflectionTestUtils.setField(appointment, "id", id);
        ReflectionTestUtils.setField(appointment, "status", status);
        return appointment;
    }

    private enum BookingAction {
        CONFIRM(AppointmentStatus.CONFIRMED),
        COMPLETE(AppointmentStatus.COMPLETED),
        NO_SHOW(AppointmentStatus.NO_SHOW),
        CANCEL(AppointmentStatus.CANCELLED);

        private final AppointmentStatus target;

        BookingAction(AppointmentStatus target) {
            this.target = target;
        }

        AppointmentStatus target() {
            return target;
        }
    }
}

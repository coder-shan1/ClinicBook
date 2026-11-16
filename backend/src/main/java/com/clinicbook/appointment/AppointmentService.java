package com.clinicbook.appointment;

import com.clinicbook.appointment.dto.AppointmentResponse;
import com.clinicbook.appointment.dto.CreateAppointmentRequest;
import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.config.BookingProperties;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.schedule.TimeSlot;
import com.clinicbook.schedule.TimeSlotRepository;
import com.clinicbook.schedule.TimeSlotStatus;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Booking engine. Slot reservation is guarded by the slot's optimistic-lock version and
 * a unique index on the active appointment's slot, so two requests can never both win.
 */
@Service
public class AppointmentService {

    private static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("startAt"));

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              TimeSlotRepository timeSlotRepository,
                              UserRepository userRepository,
                              DoctorRepository doctorRepository,
                              BookingProperties bookingProperties,
                              Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    @Transactional
    public AppointmentResponse book(Long patientId, CreateAppointmentRequest request) {
        Instant now = clock.instant();

        // Locks the patient row so concurrent requests from the same patient cannot both
        // pass the active-booking limit. Different patients lock different rows.
        User patient = userRepository.findByIdForUpdate(patientId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        long activeBookings = appointmentRepository.countByPatientIdAndStatusIn(patientId, ACTIVE_STATUSES);
        if (activeBookings >= bookingProperties.maxActivePerPatient()) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_LIMIT_REACHED",
                    "You already hold the maximum number of active appointments");
        }

        TimeSlot slot = timeSlotRepository.findById(request.slotId())
                .orElseThrow(AppointmentService::slotNotFound);

        if (!slot.getStartAt().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_IN_PAST", "Slot is in the past");
        }
        if (!slot.getDoctor().isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCTOR_INACTIVE",
                    "This doctor is not accepting bookings");
        }
        if (slot.getStatus() != TimeSlotStatus.AVAILABLE) {
            throw slotTaken();
        }

        slot.markBooked();
        try {
            timeSlotRepository.saveAndFlush(slot);
        } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw slotTaken();
        }

        Appointment appointment = new Appointment(
                patient, slot.getDoctor(), slot, slot.getStartAt(), slot.getEndAt());
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            // The unique index on slot_id is the database-level guard against a booking
            // that slipped past the optimistic-lock check.
            throw slotTaken();
        }
        return AppointmentResponse.from(appointment);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> listMine(JwtPrincipal principal, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), DEFAULT_SORT);
        Page<Appointment> appointments = switch (principal.role()) {
            case PATIENT -> appointmentRepository.findAllByPatientId(principal.id(), pageable);
            case DOCTOR -> appointmentRepository.findAllByDoctorId(
                    requireOwnDoctorId(principal.id()), pageable);
            case ADMIN -> throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Administrators do not have a personal appointment list");
        };
        return PageResponse.from(appointments, AppointmentResponse::from);
    }

    @Transactional
    public AppointmentResponse confirm(Long appointmentId, JwtPrincipal principal) {
        Appointment appointment = requireAccessibleForUpdate(appointmentId, principal);
        ensureTransition(appointment, AppointmentStatus.CONFIRMED);
        appointment.transitionTo(AppointmentStatus.CONFIRMED);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse complete(Long appointmentId, JwtPrincipal principal) {
        Appointment appointment = requireAccessibleForUpdate(appointmentId, principal);
        ensureTransition(appointment, AppointmentStatus.COMPLETED);
        appointment.transitionTo(AppointmentStatus.COMPLETED);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse noShow(Long appointmentId, JwtPrincipal principal) {
        Appointment appointment = requireAccessibleForUpdate(appointmentId, principal);
        ensureTransition(appointment, AppointmentStatus.NO_SHOW);
        appointment.transitionTo(AppointmentStatus.NO_SHOW);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(Long appointmentId, JwtPrincipal principal) {
        Appointment appointment = requireAccessibleForUpdate(appointmentId, principal);
        ensureTransition(appointment, AppointmentStatus.CANCELLED);

        if (principal.role() == Role.PATIENT && isInsideCancellationCutoff(appointment)) {
            throw new ApiException(HttpStatus.CONFLICT, "CANCELLATION_WINDOW_CLOSED",
                    "Appointments can only be cancelled before the cancellation cutoff");
        }

        appointment.transitionTo(AppointmentStatus.CANCELLED);
        releaseSlot(appointment);
        return AppointmentResponse.from(appointment);
    }

    private boolean isInsideCancellationCutoff(Appointment appointment) {
        Instant cutoff = clock.instant().plus(bookingProperties.cancelCutoff());
        return appointment.getStartAt().isBefore(cutoff);
    }

    private void releaseSlot(Appointment appointment) {
        TimeSlot slot = appointment.getSlot();
        if (slot == null) {
            return;
        }
        if (slot.getStatus() == TimeSlotStatus.BOOKED) {
            slot.markAvailable();
            timeSlotRepository.saveAndFlush(slot);
        }
        appointment.detachSlot();
    }

    /**
     * Locks the appointment row for the duration of a state change so two concurrent
     * requests cannot both apply a transition.
     */
    private Appointment requireAccessibleForUpdate(Long appointmentId, JwtPrincipal principal) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(AppointmentService::appointmentNotFound);
        requireAccess(appointment, principal);
        return appointment;
    }

    private void requireAccess(Appointment appointment, JwtPrincipal principal) {
        Role role = principal.role();
        if (role == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied");
        }
        switch (role) {
            case ADMIN -> {
                // Administrators may manage any appointment.
            }
            case PATIENT -> {
                if (!appointment.getPatient().getId().equals(principal.id())) {
                    throw appointmentNotFound();
                }
            }
            case DOCTOR -> {
                Long doctorId = requireOwnDoctorId(principal.id());
                if (!appointment.getDoctor().getId().equals(doctorId)) {
                    throw appointmentNotFound();
                }
            }
        }
    }

    private Long requireOwnDoctorId(Long userId) {
        return doctorRepository.findByUserId(userId)
                .map(Doctor::getId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "DOCTOR_PROFILE_NOT_FOUND", "Doctor profile not found"));
    }

    private static void ensureTransition(Appointment appointment, AppointmentStatus target) {
        if (!appointment.getStatus().canTransitionTo(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPOINTMENT_STATE",
                    "Cannot change appointment from " + appointment.getStatus() + " to " + target);
        }
    }

    private static ApiException appointmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "Appointment not found");
    }

    private static ApiException slotNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Slot not found");
    }

    private static ApiException slotTaken() {
        return new ApiException(HttpStatus.CONFLICT, "SLOT_TAKEN",
                "This slot has already been booked");
    }
}

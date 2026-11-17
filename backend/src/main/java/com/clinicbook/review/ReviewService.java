package com.clinicbook.review;

import com.clinicbook.appointment.Appointment;
import com.clinicbook.appointment.AppointmentRepository;
import com.clinicbook.appointment.AppointmentStatus;
import com.clinicbook.common.error.ApiException;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.review.dto.CreateReviewRequest;
import com.clinicbook.review.dto.ReviewResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final DoctorRepository doctorRepository;

    public ReviewService(AppointmentRepository appointmentRepository,
                         ReviewRepository reviewRepository,
                         DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.reviewRepository = reviewRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    public ReviewResponse create(Long patientId, CreateReviewRequest request) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(request.appointmentId())
                .orElseThrow(ReviewService::appointmentNotFound);

        if (!appointment.getPatient().getId().equals(patientId)) {
            // Hide other patients' appointments rather than confirming they exist.
            throw appointmentNotFound();
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_COMPLETED",
                    "Only completed appointments can be reviewed");
        }
        if (reviewRepository.existsByAppointmentId(appointment.getId())) {
            throw alreadyReviewed();
        }

        // Locking the doctor serialises the running-average update across concurrent
        // reviews for the same doctor.
        Doctor doctor = doctorRepository.findByIdForUpdate(appointment.getDoctor().getId())
                .orElseThrow(ReviewService::doctorNotFound);

        Review review = new Review(
                appointment,
                appointment.getPatient(),
                doctor,
                request.rating(),
                normaliseComment(request.comment()));
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            // The unique appointment index wins if another request slipped past the check.
            throw alreadyReviewed();
        }

        doctor.addRating(request.rating());
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForDoctor(Long doctorId, int page, int size) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .filter(Doctor::isActive)
                .orElseThrow(ReviewService::doctorNotFound);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt")));
        return PageResponse.from(
                reviewRepository.findAllByDoctorId(doctor.getId(), pageable),
                ReviewResponse::from);
    }

    private static String normaliseComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApiException appointmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "Appointment not found");
    }

    private static ApiException doctorNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND", "Doctor not found");
    }

    private static ApiException alreadyReviewed() {
        return new ApiException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS",
                "This appointment has already been reviewed");
    }
}

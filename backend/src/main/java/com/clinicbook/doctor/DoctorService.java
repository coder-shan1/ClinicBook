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
import com.clinicbook.schedule.TimeSlotRepository;
import com.clinicbook.schedule.TimeSlotStatus;
import com.clinicbook.schedule.dto.TimeSlotResponse;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class DoctorService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("user.fullName"));

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final PasswordEncoder passwordEncoder;
    private final TimeSlotRepository timeSlotRepository;
    private final SlotGenerationService slotGenerationService;
    private final ClinicProperties clinicProperties;
    private final Clock clock;

    public DoctorService(DoctorRepository doctorRepository,
                         UserRepository userRepository,
                         SpecialtyRepository specialtyRepository,
                         PasswordEncoder passwordEncoder,
                         TimeSlotRepository timeSlotRepository,
                         SlotGenerationService slotGenerationService,
                         ClinicProperties clinicProperties,
                         Clock clock) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.specialtyRepository = specialtyRepository;
        this.passwordEncoder = passwordEncoder;
        this.timeSlotRepository = timeSlotRepository;
        this.slotGenerationService = slotGenerationService;
        this.clinicProperties = clinicProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorSummaryResponse> search(Long specialtyId, String q,
                                                      int page, int size, String sort) {
        Pageable pageable = pageable(page, size, sort);
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<Doctor> doctors = doctorRepository.search(specialtyId, query, pageable);
        return PageResponse.from(doctors, DoctorSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public DoctorResponse getPublic(Long id) {
        return doctorRepository.findWithProfileById(id)
                .filter(Doctor::isActive)
                .map(DoctorResponse::from)
                .orElseThrow(() -> doctorNotFound());
    }

    @Transactional(readOnly = true)
    public List<TimeSlotResponse> slots(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .filter(Doctor::isActive)
                .orElseThrow(() -> doctorNotFound());

        ZoneId zone = clinicProperties.zone();
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(zone));
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();

        return timeSlotRepository
                .findAllByDoctorIdAndStatusAndStartAtBetweenOrderByStartAtAsc(
                        doctor.getId(), TimeSlotStatus.AVAILABLE, from, to)
                .stream()
                .map(TimeSlotResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse me(Long userId) {
        return DoctorResponse.from(requireDoctorForUser(userId));
    }

    @Transactional
    public DoctorResponse updateProfile(Long userId, UpdateDoctorProfileRequest request) {
        Doctor doctor = requireDoctorForUser(userId);
        doctor.update(requireSpecialty(request.specialtyId()), request.bio(), request.consultationFee());
        return DoctorResponse.from(doctor);
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorResponse> listForAdmin(int page, int size, String sort) {
        Page<Doctor> doctors = doctorRepository.findAll(pageable(page, size, sort));
        return PageResponse.from(doctors, DoctorResponse::from);
    }

    @Transactional(readOnly = true)
    public DoctorResponse getForAdmin(Long id) {
        return doctorRepository.findWithProfileById(id)
                .map(DoctorResponse::from)
                .orElseThrow(() -> doctorNotFound());
    }

    @Transactional
    public DoctorResponse create(CreateDoctorRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }
        Specialty specialty = requireSpecialty(request.specialtyId());

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                Role.DOCTOR,
                request.fullName().trim(),
                normalisePhone(request.phone()));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw emailTaken();
        }

        Doctor doctor = new Doctor(user, specialty, request.bio(), request.consultationFee());
        doctorRepository.save(doctor);
        return DoctorResponse.from(doctor);
    }

    @Transactional
    public DoctorResponse update(Long id, UpdateDoctorRequest request) {
        Doctor doctor = doctorRepository.findWithProfileById(id).orElseThrow(() -> doctorNotFound());
        doctor.getUser().updateProfile(request.fullName().trim(), normalisePhone(request.phone()));
        doctor.update(requireSpecialty(request.specialtyId()), request.bio(), request.consultationFee());

        if (request.active()) {
            doctor.activate();
            doctor.getUser().enable();
            slotGenerationService.regenerate(doctor.getId());
        } else {
            doctor.deactivate();
            doctor.getUser().disable();
        }
        return DoctorResponse.from(doctor);
    }

    @Transactional
    public void deactivate(Long id) {
        Doctor doctor = doctorRepository.findById(id).orElseThrow(() -> doctorNotFound());
        doctor.deactivate();
        doctor.getUser().disable();
    }

    private Doctor requireDoctorForUser(Long userId) {
        return doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "DOCTOR_PROFILE_NOT_FOUND", "Doctor profile not found"));
    }

    private Specialty requireSpecialty(Long id) {
        return specialtyRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND", "Specialty not found"));
    }

    private static Pageable pageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(safePage, safeSize, parseSort(sort));
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", 2);
        String field = switch (parts[0].trim()) {
            case "fullName" -> "user.fullName";
            case "specialty" -> "specialty.name";
            case "consultationFee" -> "consultationFee";
            case "createdAt" -> "createdAt";
            default -> "user.fullName";
        };
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, field));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalisePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApiException doctorNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND", "Doctor not found");
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
    }
}

package com.clinicbook.doctor;

import com.clinicbook.common.error.ApiException;
import com.clinicbook.doctor.dto.SpecialtyRequest;
import com.clinicbook.doctor.dto.SpecialtyResponse;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;

    public SpecialtyService(SpecialtyRepository specialtyRepository, DoctorRepository doctorRepository) {
        this.specialtyRepository = specialtyRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> list() {
        return specialtyRepository.findAll(Sort.by(Sort.Order.asc("name"))).stream()
                .map(SpecialtyResponse::from)
                .toList();
    }

    @Transactional
    public SpecialtyResponse create(SpecialtyRequest request) {
        String name = request.name().trim();
        if (specialtyRepository.existsByNameIgnoreCase(name)) {
            throw specialtyTaken();
        }
        Specialty specialty = specialtyRepository.save(new Specialty(name, request.description()));
        return SpecialtyResponse.from(specialty);
    }

    @Transactional
    public SpecialtyResponse update(Long id, SpecialtyRequest request) {
        Specialty specialty = requireSpecialty(id);
        String name = request.name().trim();
        if (specialtyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw specialtyTaken();
        }
        specialty.update(name, request.description());
        return SpecialtyResponse.from(specialty);
    }

    @Transactional
    public void delete(Long id) {
        Specialty specialty = requireSpecialty(id);
        if (doctorRepository.existsBySpecialtyId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "SPECIALTY_IN_USE",
                    "Specialty is assigned to at least one doctor");
        }
        specialtyRepository.delete(specialty);
    }

    private Specialty requireSpecialty(Long id) {
        return specialtyRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND", "Specialty not found"));
    }

    private static ApiException specialtyTaken() {
        return new ApiException(HttpStatus.CONFLICT, "SPECIALTY_TAKEN",
                "A specialty with this name already exists");
    }
}

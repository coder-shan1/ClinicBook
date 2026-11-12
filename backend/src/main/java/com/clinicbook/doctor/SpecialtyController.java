package com.clinicbook.doctor;

import com.clinicbook.doctor.dto.SpecialtyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/specialties", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Specialties", description = "Public specialty catalogue")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    public SpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @GetMapping
    @Operation(summary = "List specialties", operationId = "listSpecialties")
    @ApiResponse(responseCode = "200", description = "Specialties ordered by name")
    public List<SpecialtyResponse> list() {
        return specialtyService.list();
    }
}

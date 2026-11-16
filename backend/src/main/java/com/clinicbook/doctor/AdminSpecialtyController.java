package com.clinicbook.doctor;

import com.clinicbook.common.error.ApiError;
import com.clinicbook.doctor.dto.SpecialtyRequest;
import com.clinicbook.doctor.dto.SpecialtyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/specialties", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Specialties", description = "Administrator specialty management")
public class AdminSpecialtyController {

    private final SpecialtyService specialtyService;

    public AdminSpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a specialty", operationId = "adminCreateSpecialty")
    @ApiResponse(responseCode = "201", description = "Specialty created")
    @ApiResponse(responseCode = "400", description = "Malformed body or failed field validation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Specialty name already exists",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public SpecialtyResponse create(@Valid @RequestBody SpecialtyRequest request) {
        return specialtyService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename or update a specialty", operationId = "adminUpdateSpecialty")
    @ApiResponse(responseCode = "200", description = "Specialty updated")
    @ApiResponse(responseCode = "400", description = "Malformed body or failed field validation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Specialty not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Specialty name already exists",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public SpecialtyResponse update(@PathVariable Long id,
                                    @Valid @RequestBody SpecialtyRequest request) {
        return specialtyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an unused specialty", operationId = "adminDeleteSpecialty")
    @ApiResponse(responseCode = "204", description = "Specialty deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Specialty not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Specialty is assigned to a doctor",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public void delete(@PathVariable Long id) {
        specialtyService.delete(id);
    }
}

package com.clinicbook.doctor;

import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.common.error.ApiError;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.doctor.dto.DoctorResponse;
import com.clinicbook.doctor.dto.DoctorSummaryResponse;
import com.clinicbook.doctor.dto.UpdateDoctorProfileRequest;
import com.clinicbook.schedule.dto.TimeSlotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/doctors", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Doctors", description = "Public doctor search plus doctor self-service")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    @Operation(summary = "Search active doctors", operationId = "searchDoctors")
    @ApiResponse(responseCode = "200", description = "Page of matching doctors")
    @ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameter",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public PageResponse<DoctorSummaryResponse> search(
            @RequestParam(required = false) Long specialty,
            @RequestParam(required = false) @Size(max = 100, message = "must be at most 100 characters") String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size,
            @RequestParam(defaultValue = "fullName,asc") String sort) {
        return doctorService.search(specialty, q, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one active doctor", operationId = "getDoctor")
    @ApiResponse(responseCode = "200", description = "Doctor profile")
    @ApiResponse(responseCode = "404", description = "Doctor not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public DoctorResponse get(@PathVariable Long id) {
        return doctorService.getPublic(id);
    }

    @GetMapping("/{id}/slots")
    @Operation(summary = "List available slots for a doctor on a date", operationId = "listDoctorSlots")
    @ApiResponse(responseCode = "200", description = "Available slots, ordered by start time")
    @ApiResponse(responseCode = "404", description = "Doctor not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public List<TimeSlotResponse> slots(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return doctorService.slots(id, date);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Return the authenticated doctor's profile", operationId = "getMyDoctorProfile")
    @ApiResponse(responseCode = "200", description = "Own doctor profile")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a doctor",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Doctor profile not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public DoctorResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        return doctorService.me(principal.id());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update the authenticated doctor's profile", operationId = "updateMyDoctorProfile")
    @ApiResponse(responseCode = "200", description = "Updated doctor profile")
    @ApiResponse(responseCode = "400", description = "Malformed body or failed field validation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a doctor",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Doctor profile or specialty not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public DoctorResponse updateMe(@AuthenticationPrincipal JwtPrincipal principal,
                                   @Valid @RequestBody UpdateDoctorProfileRequest request) {
        return doctorService.updateProfile(principal.id(), request);
    }
}

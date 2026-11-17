package com.clinicbook.appointment;

import com.clinicbook.appointment.dto.AppointmentResponse;
import com.clinicbook.appointment.dto.CreateAppointmentRequest;
import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.common.error.ApiError;
import com.clinicbook.common.page.PageResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping(value = "/api/v1/appointments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Appointments", description = "Booking and appointment lifecycle")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Book a slot", operationId = "bookAppointment",
            description = "Reserves an available slot for the authenticated patient. "
                    + "Concurrent attempts on the same slot return 409 SLOT_TAKEN.")
    @ApiResponse(responseCode = "201", description = "Appointment created in PENDING state",
            headers = @Header(name = "Location", description = "URL of the new appointment",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = "Malformed body, failed validation or slot in the past",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a patient",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Slot not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "SLOT_TAKEN, BOOKING_LIMIT_REACHED or DOCTOR_INACTIVE",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<AppointmentResponse> book(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse appointment = appointmentService.book(principal.id(), request);
        return ResponseEntity
                .created(URI.create("/api/v1/appointments/" + appointment.id()))
                .body(appointment);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List the authenticated patient's or doctor's appointments",
            operationId = "listMyAppointments")
    @ApiResponse(responseCode = "200", description = "Appointments ordered by start time")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a patient or doctor",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Doctor profile not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public PageResponse<AppointmentResponse> listMine(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must be at least 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {
        return appointmentService.listMine(principal, page, size);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancel an appointment", operationId = "cancelAppointment",
            description = "Releases the slot back to AVAILABLE and keeps the appointment "
                    + "as history. Patients are bound by the cancellation cutoff.")
    @ApiResponse(responseCode = "200", description = "Appointment cancelled")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller may not cancel appointments",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Appointment not found or not owned by the caller",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "INVALID_APPOINTMENT_STATE or CANCELLATION_WINDOW_CLOSED",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AppointmentResponse cancel(@AuthenticationPrincipal JwtPrincipal principal,
                                      @PathVariable Long id) {
        return appointmentService.cancel(id, principal);
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Confirm a pending appointment", operationId = "confirmAppointment")
    @ApiResponse(responseCode = "200", description = "Appointment confirmed")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller may not confirm appointments",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Appointment not found or not owned by the caller",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "INVALID_APPOINTMENT_STATE",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AppointmentResponse confirm(@AuthenticationPrincipal JwtPrincipal principal,
                                       @PathVariable Long id) {
        return appointmentService.confirm(id, principal);
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mark a confirmed appointment as completed", operationId = "completeAppointment")
    @ApiResponse(responseCode = "200", description = "Appointment completed")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller may not complete appointments",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Appointment not found or not owned by the caller",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "INVALID_APPOINTMENT_STATE",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AppointmentResponse complete(@AuthenticationPrincipal JwtPrincipal principal,
                                        @PathVariable Long id) {
        return appointmentService.complete(id, principal);
    }

    @PatchMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mark a confirmed appointment as a no-show", operationId = "markAppointmentNoShow")
    @ApiResponse(responseCode = "200", description = "Appointment marked NO_SHOW")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller may not mark no-shows",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Appointment not found or not owned by the caller",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "INVALID_APPOINTMENT_STATE",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AppointmentResponse noShow(@AuthenticationPrincipal JwtPrincipal principal,
                                      @PathVariable Long id) {
        return appointmentService.noShow(id, principal);
    }
}

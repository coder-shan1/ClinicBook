package com.clinicbook.schedule;

import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.common.error.ApiError;
import com.clinicbook.schedule.dto.ReplaceWorkingHoursRequest;
import com.clinicbook.schedule.dto.SlotGenerationResponse;
import com.clinicbook.schedule.dto.TimeOffRequest;
import com.clinicbook.schedule.dto.TimeOffResponse;
import com.clinicbook.schedule.dto.WorkingHoursResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/doctors/me", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('DOCTOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Doctor schedule", description = "Working hours, time off and slot regeneration")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/working-hours")
    @Operation(summary = "List the authenticated doctor's working hours")
    @ApiResponse(responseCode = "200", description = "Working hours ordered by day and start time")
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
    public List<WorkingHoursResponse> getWorkingHours(@AuthenticationPrincipal JwtPrincipal principal) {
        return scheduleService.getWorkingHours(principal.id());
    }

    @PutMapping("/working-hours")
    @Operation(summary = "Replace the authenticated doctor's working hours and regenerate slots")
    @ApiResponse(responseCode = "200", description = "Stored working hours")
    @ApiResponse(responseCode = "400", description = "Invalid or overlapping working hours",
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
    @ApiResponse(responseCode = "404", description = "Doctor profile not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public List<WorkingHoursResponse> replaceWorkingHours(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ReplaceWorkingHoursRequest request) {
        return scheduleService.replaceWorkingHours(principal.id(), request);
    }

    @GetMapping("/time-off")
    @Operation(summary = "List the authenticated doctor's time off")
    @ApiResponse(responseCode = "200", description = "Time off ordered by start time")
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
    public List<TimeOffResponse> getTimeOff(@AuthenticationPrincipal JwtPrincipal principal) {
        return scheduleService.getTimeOff(principal.id());
    }

    @PostMapping("/time-off")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add time off and regenerate slots")
    @ApiResponse(responseCode = "201", description = "Time off created")
    @ApiResponse(responseCode = "400", description = "Invalid time range",
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
    @ApiResponse(responseCode = "404", description = "Doctor profile not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Time off overlaps an existing period",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public TimeOffResponse addTimeOff(@AuthenticationPrincipal JwtPrincipal principal,
                                      @Valid @RequestBody TimeOffRequest request) {
        return scheduleService.addTimeOff(principal.id(), request);
    }

    @DeleteMapping("/time-off/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete time off and regenerate slots")
    @ApiResponse(responseCode = "204", description = "Time off deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            headers = @Header(name = "WWW-Authenticate", description = "Bearer challenge",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a doctor",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Time off not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public void deleteTimeOff(@AuthenticationPrincipal JwtPrincipal principal,
                              @PathVariable Long id) {
        scheduleService.deleteTimeOff(principal.id(), id);
    }

    @PostMapping("/slots/regenerate")
    @Operation(summary = "Regenerate the next 30 days of slots immediately")
    @ApiResponse(responseCode = "200", description = "Slot generation summary")
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
    public SlotGenerationResponse regenerateSlots(@AuthenticationPrincipal JwtPrincipal principal) {
        SlotGenerationResult result = scheduleService.regenerateSlots(principal.id());
        return new SlotGenerationResponse(result.generatedAt(), result.created(), result.removed(),
                result.resized(), result.available());
    }
}

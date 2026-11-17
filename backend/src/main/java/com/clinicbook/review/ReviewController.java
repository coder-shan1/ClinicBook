package com.clinicbook.review;

import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.common.error.ApiError;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.review.dto.CreateReviewRequest;
import com.clinicbook.review.dto.ReviewResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Reviews", description = "Ratings for completed appointments")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('PATIENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Review a completed appointment", operationId = "createReview",
            description = "Patients may review an appointment once, after it reaches COMPLETED. "
                    + "The doctor's average rating is updated immediately.")
    @ApiResponse(responseCode = "201", description = "Review created",
            headers = @Header(name = "Location", description = "URL of the new review",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = "Malformed body or rating outside 1..5",
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
    @ApiResponse(responseCode = "404", description = "Appointment not found or not owned by the caller",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "APPOINTMENT_NOT_COMPLETED or REVIEW_ALREADY_EXISTS",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<ReviewResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse review = reviewService.create(principal.id(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/doctors/" + review.doctorId() + "/reviews"))
                .body(review);
    }

    @GetMapping("/doctors/{doctorId}/reviews")
    @Operation(summary = "List a doctor's reviews", operationId = "listDoctorReviews",
            description = "Public, newest first. Patient names are masked.")
    @ApiResponse(responseCode = "200", description = "Reviews ordered by creation time, newest first")
    @ApiResponse(responseCode = "400", description = "Invalid page or size",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Doctor not found or inactive",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public PageResponse<ReviewResponse> list(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must be at least 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {
        return reviewService.listForDoctor(doctorId, page, size);
    }
}

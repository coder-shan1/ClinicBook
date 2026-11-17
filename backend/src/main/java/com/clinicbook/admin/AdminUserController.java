package com.clinicbook.admin;

import com.clinicbook.admin.dto.AdminUserResponse;
import com.clinicbook.admin.dto.UpdateUserRoleRequest;
import com.clinicbook.admin.dto.UpdateUserStatusRequest;
import com.clinicbook.auth.JwtPrincipal;
import com.clinicbook.common.error.ApiError;
import com.clinicbook.common.page.PageResponse;
import com.clinicbook.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "Administrator statistics and user management")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "List user accounts", operationId = "listAdminUsers",
            description = "Newest first. Filter by role and by email or full name.")
    @ApiResponse(responseCode = "200", description = "Paginated user accounts")
    @ApiResponse(responseCode = "400", description = "Invalid role, page or size",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public PageResponse<AdminUserResponse> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must be at least 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {
        return adminUserService.list(role, q, page, size);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable an account", operationId = "updateAdminUserStatus",
            description = "Disabling revokes refresh tokens and, for doctors, deactivates the "
                    + "doctor profile. The last enabled administrator cannot be disabled.")
    @ApiResponse(responseCode = "200", description = "Account updated")
    @ApiResponse(responseCode = "400", description = "Malformed body or missing enabled flag",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "SELF_MODIFICATION_NOT_ALLOWED or LAST_ADMIN",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AdminUserResponse updateStatus(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return adminUserService.updateStatus(id, request, principal);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change an account role", operationId = "updateAdminUserRole",
            description = "Doctor roles are managed through the doctor endpoints. Role changes "
                    + "revoke refresh tokens and the last enabled administrator cannot be demoted.")
    @ApiResponse(responseCode = "200", description = "Role updated")
    @ApiResponse(responseCode = "400", description = "Malformed body or invalid role",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "SELF_MODIFICATION_NOT_ALLOWED, LAST_ADMIN "
            + "or DOCTOR_ROLE_MANAGED_SEPARATELY",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public AdminUserResponse updateRole(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return adminUserService.updateRole(id, request, principal);
    }
}

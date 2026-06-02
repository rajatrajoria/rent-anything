package com.rajat.rent_anything.user.api;

import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.security.CustomUserDetails;
import com.rajat.rent_anything.user.application.AdminService;
import com.rajat.rent_anything.user.records.request.UpdateTrustStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administrative controller exposing privileged user-management operations.
 *
 * Access to all endpoints in this controller is restricted to users
 * with the ADMIN role.
 *
 * Responsibilities:
 * - Manage user trust status
 * - Perform administrative user actions
 *
 * Security:
 * The @PreAuthorize annotation ensures that only authenticated
 * administrators can access these endpoints.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminControllers {

    /**
     * Service containing business logic for administrative operations.
     */
    private final AdminService adminService;

    /**
     * Updates the trust status of a user.
     *
     * Trust status is used to indicate whether a user is considered
     * trusted within the platform and may be used to unlock
     * additional platform capabilities or reduce restrictions.
     *
     * Workflow:
     * 1. Validate request payload.
     * 2. Identify the administrator performing the action.
     * 3. Update the target user's trust status.
     * 4. Return success response.
     *
     * @param userId target user identifier
     * @param request requested trust status update
     * @param userDetails currently authenticated administrator
     * @return success response
     */
    @PatchMapping("/{userId}/trust-status")
    public ResponseEntity<ApiResponse<Void>> updateUserTrustStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateTrustStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        log.info(
                "Admin {} updating trust status for userId: {}, trusted: {}",
                userDetails.getDomainUser().getId(),
                userId,
                request.status()
        );

        Long adminId = userDetails.getDomainUser().getId();

        adminService.updateUserTrustStatus(
                adminId,
                userId,
                request.status()
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

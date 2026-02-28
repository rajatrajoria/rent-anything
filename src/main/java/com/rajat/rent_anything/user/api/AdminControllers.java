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

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminControllers {
    private final AdminService adminService;

    @PatchMapping("/{userId}/trust-status")
    public ResponseEntity<ApiResponse<Void>> updateUserTrustStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateTrustStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("Admin {} updating trust status for userId: {}, trusted: {}", userDetails.getDomainUser().getId(), userId, request.status());
        Long adminId = userDetails.getDomainUser().getId();
        adminService.updateUserTrustStatus(adminId, userId, request.status());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

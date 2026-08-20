package com.rajat.rent_anything.kyc.api;

import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.kyc.application.KycAdminService;
import com.rajat.rent_anything.kyc.enums.KycStatus;
import com.rajat.rent_anything.kyc.records.request.RejectKycRequest;
import com.rajat.rent_anything.kyc.records.response.AdminKycDetailResponse;
import com.rajat.rent_anything.kyc.records.response.AdminKycListResponse;
import com.rajat.rent_anything.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administrative controller for reviewing KYC submissions.
 * <p>
 * Access to all endpoints in this controller is restricted to users with
 * the ADMIN role, matching {@code AdminControllers}' convention.
 */
@Slf4j
@RestController
@RequestMapping("/admin/kyc")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKycController {

    private final KycAdminService kycAdminService;

    public AdminKycController(KycAdminService kycAdminService) {
        this.kycAdminService = kycAdminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminKycListResponse>> listSubmissions(
            @RequestParam(value = "status", required = false) KycStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long adminId = userDetails.getDomainUser().getId();
        AdminKycListResponse response = kycAdminService.listSubmissions(adminId, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminKycDetailResponse>> getSubmissionDetail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long adminId = userDetails.getDomainUser().getId();
        AdminKycDetailResponse response = kycAdminService.getSubmissionDetail(adminId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Approves a submission. Atomically flips the submitting user's trust
     * status to TRUSTED.
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long adminId = userDetails.getDomainUser().getId();
        log.info("Admin {} approving KYC submission {}", adminId, id);
        kycAdminService.approve(adminId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Rejects a submission with a required reason. Atomically sets the
     * submitting user's trust status to UNTRUSTED.
     */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable("id") Long id,
            @Valid @RequestBody RejectKycRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long adminId = userDetails.getDomainUser().getId();
        log.info("Admin {} rejecting KYC submission {}", adminId, id);
        kycAdminService.reject(adminId, id, request.reason());
        return ResponseEntity.ok(ApiResponse.success());
    }
}

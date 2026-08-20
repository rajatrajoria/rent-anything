package com.rajat.rent_anything.kyc.api;

import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.kyc.application.KycService;
import com.rajat.rent_anything.kyc.enums.IdDocumentType;
import com.rajat.rent_anything.kyc.records.request.SubmitKycCommand;
import com.rajat.rent_anything.kyc.records.response.KycSubmissionResponseDto;
import com.rajat.rent_anything.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * REST controller for a user's own KYC submission.
 * <p>
 * Requires authentication only (no trust/role gate) — the whole point of
 * this flow is to be the path an UNTRUSTED user takes to become TRUSTED.
 */
@Slf4j
@RestController
@RequestMapping("/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    /**
     * Submits (or resubmits, if previously rejected) KYC details and
     * documents.
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<KycSubmissionResponseDto>> submit(
            @RequestParam("legalName") String legalName,
            @RequestParam("dateOfBirth") LocalDate dateOfBirth,
            @RequestParam("addressLine1") String addressLine1,
            @RequestParam(value = "addressLine2", required = false) String addressLine2,
            @RequestParam("city") String city,
            @RequestParam("state") String state,
            @RequestParam("postalCode") String postalCode,
            @RequestParam("country") String country,
            @RequestParam("idDocumentType") IdDocumentType idDocumentType,
            @RequestParam("idDocument") MultipartFile idDocument,
            @RequestParam("selfie") MultipartFile selfie,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getDomainUser().getId();
        log.info("User {} submitting KYC", userId);

        SubmitKycCommand command = new SubmitKycCommand(
                legalName, dateOfBirth, addressLine1, addressLine2,
                city, state, postalCode, country, idDocumentType
        );

        KycSubmissionResponseDto response = kycService.submit(userId, command, idDocument, selfie);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Returns the caller's own submission, or {@code data: null} if they
     * haven't submitted yet.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<KycSubmissionResponseDto>> getMySubmission(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getDomainUser().getId();
        KycSubmissionResponseDto response = kycService.getMySubmission(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

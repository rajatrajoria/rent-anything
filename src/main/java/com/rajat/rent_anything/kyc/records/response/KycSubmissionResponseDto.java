package com.rajat.rent_anything.kyc.records.response;

import com.rajat.rent_anything.kyc.enums.IdDocumentType;
import com.rajat.rent_anything.kyc.enums.KycStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The caller's own view of their KYC submission.
 */
public record KycSubmissionResponseDto(
        Long id,
        KycStatus status,
        String legalName,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        IdDocumentType idDocumentType,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

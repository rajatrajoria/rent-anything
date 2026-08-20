package com.rajat.rent_anything.kyc.records.response;

import com.rajat.rent_anything.kyc.enums.IdDocumentType;
import com.rajat.rent_anything.kyc.enums.KycStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Full submission detail for admin review, including short-lived presigned
 * URLs for both documents. Never expose this DTO from a public route.
 */
public record AdminKycDetailResponse(
        Long id,
        Long userId,
        String userEmail,
        String legalName,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        IdDocumentType idDocumentType,
        String idDocumentImageUrl,
        String selfieImageUrl,
        KycStatus status,
        String rejectionReason,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

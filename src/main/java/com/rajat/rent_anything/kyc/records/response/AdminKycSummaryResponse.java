package com.rajat.rent_anything.kyc.records.response;

import com.rajat.rent_anything.kyc.enums.KycStatus;

import java.time.LocalDateTime;

public record AdminKycSummaryResponse(
        Long id,
        Long userId,
        String userEmail,
        String legalName,
        KycStatus status,
        LocalDateTime createdAt
) {
}

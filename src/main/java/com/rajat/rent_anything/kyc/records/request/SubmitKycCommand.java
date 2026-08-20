package com.rajat.rent_anything.kyc.records.request;

import com.rajat.rent_anything.kyc.enums.IdDocumentType;

import java.time.LocalDate;

public record SubmitKycCommand(
        String legalName,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        IdDocumentType idDocumentType
) {
}

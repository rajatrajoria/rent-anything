package com.rajat.rent_anything.kyc.records.request;

import jakarta.validation.constraints.NotBlank;

public record RejectKycRequest(
        @NotBlank String reason
) {
}

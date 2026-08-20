package com.rajat.rent_anything.kyc.records.response;

import java.util.List;

public record AdminKycListResponse(
        List<AdminKycSummaryResponse> submissions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

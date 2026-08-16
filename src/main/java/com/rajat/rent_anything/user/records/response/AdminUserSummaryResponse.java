package com.rajat.rent_anything.user.records.response;

import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.enums.UserRole;

import java.time.LocalDateTime;

public record AdminUserSummaryResponse(
        Long id,
        String email,
        String name,
        boolean isVerified,
        TrustStatus trustStatus,
        UserRole role,
        LocalDateTime createdAt
) {
}

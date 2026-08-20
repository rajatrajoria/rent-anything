package com.rajat.rent_anything.user.records.response;

import com.rajat.rent_anything.user.enums.TrustStatus;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String mobileNumber,
        boolean isVerified,
        String role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        TrustStatus trustStatus
) {
}

package com.rajat.rent_anything.user.records.response;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String mobileNumber,
        boolean isVerified,
        String role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

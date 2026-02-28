package com.rajat.rent_anything.user.records.response;

public record AuthResponse(
    String accessToken,
    String refreshToken
) {
}

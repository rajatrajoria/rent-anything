package com.rajat.rent_anything.user.records.request;

public record UpdatePasswordRequest(
    String currentPassword,
    String newPassword
) {
}

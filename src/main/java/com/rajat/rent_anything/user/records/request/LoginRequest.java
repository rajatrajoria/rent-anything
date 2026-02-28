package com.rajat.rent_anything.user.records.request;

public record LoginRequest(
        String email,
        String password
) { }

package com.rajat.rent_anything.booking.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingResponseDto(
        Long id,
        Long itemId,
        String itemTitle,
        Long renterId,
        Long ownerId,
        LocalDate startDate,
        LocalDate endDate,
        Double amount,
        String status,
        LocalDateTime createdAt
) {}

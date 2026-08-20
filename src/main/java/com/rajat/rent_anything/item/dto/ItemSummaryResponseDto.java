package com.rajat.rent_anything.item.dto;

import java.time.LocalDate;

public record ItemSummaryResponseDto(
        Long id,
        String title,
        String description,
        double pricePerDay,
        double depositAmount,
        String status,
        LocalDate availableFrom,
        LocalDate availableTo,
        String thumbnailUrl
) {}

package com.rajat.rent_anything.item.dto;

import java.time.LocalDate;
import java.util.List;

public record ItemDetailsResponseDto(
        Long id,
        Long ownerId,
        Long categoryId,
        String title,
        String description,
        double pricePerDay,
        double depositAmount,
        String status,
        LocalDate availableFrom,
        LocalDate availableTo,
        String thumbnailUrl,
        List<ItemImageResponseDto> images
) {}
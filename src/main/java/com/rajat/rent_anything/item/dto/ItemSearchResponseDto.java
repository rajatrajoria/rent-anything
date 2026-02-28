package com.rajat.rent_anything.item.dto;

public record ItemSearchResponseDto(
        Long itemId,
        Long ownerId,
        String title,
        String description,
        double pricePerDay,
        double distance,
        double textScore
)
{}

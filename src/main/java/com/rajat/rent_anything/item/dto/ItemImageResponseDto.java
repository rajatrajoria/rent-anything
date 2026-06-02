package com.rajat.rent_anything.item.dto;

public record ItemImageResponseDto(
        Long id,
        String imageUrl,
        boolean thumbnail,
        Integer displayOrder
) {
}